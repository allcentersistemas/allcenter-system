package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.config.BackupProperties;
import com.allcenter.modulesystem.dto.BackupConfigDto;
import com.allcenter.modulesystem.dto.BackupConfigUpdateRequest;
import com.allcenter.modulesystem.dto.BackupRunDto;
import com.allcenter.modulesystem.exception.BadRequestException;
import com.allcenter.modulesystem.model.BackupConfig;
import com.allcenter.modulesystem.model.BackupRun;
import com.allcenter.modulesystem.repository.BackupConfigRepository;
import com.allcenter.modulesystem.repository.BackupRunRepository;
import com.allcenter.modulesystem.util.JdbcUrlParser;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    private static final long CONFIG_ID = 1L;
    private static final Pattern SAFE_FILENAME = Pattern.compile("^[a-zA-Z0-9._-]+\\.sql\\.gz$");
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    private final BackupConfigRepository configRepository;
    private final BackupRunRepository runRepository;
    private final BackupProperties backupProperties;
    private final MailService mailService;

    @Value("${spring.datasource.url}")
    private String systemJdbcUrl;

    @Value("${spring.datasource.username}")
    private String systemUsername;

    @Value("${spring.datasource.password}")
    private String systemPassword;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Boolean pgDumpAvailable;

    @PostConstruct
    void init() {
        ensureConfigRow();
        ensureStorageDirectory();
    }

    @Transactional(readOnly = true)
    public BackupConfigDto getConfig() {
        BackupConfig config = ensureConfigRow();
        return BackupConfigDto.from(
                config,
                backupProperties.storageRoot(),
                mailService.isEnabled(),
                isPgDumpAvailable(),
                isBiesseConfigured());
    }

    @Transactional
    public BackupConfigDto updateConfig(BackupConfigUpdateRequest request) {
        if (!Boolean.TRUE.equals(request.saveToFolder()) && !Boolean.TRUE.equals(request.sendByEmail())) {
            throw new BadRequestException("Seleccione al menos un destino: carpeta o correo");
        }
        if (Boolean.TRUE.equals(request.sendByEmail())) {
            if (!mailService.isEnabled()) {
                throw new BadRequestException(
                        "El correo está desactivado. Configure APP_MAIL_ENABLED=true y SMTP en el servidor.");
            }
            if (parseRecipients(request.emailRecipients()).isEmpty()) {
                throw new BadRequestException("Indique al menos un correo destino");
            }
        }
        if (Boolean.TRUE.equals(request.includeBiesseDb()) && !isBiesseConfigured()) {
            throw new BadRequestException(
                    "La base Biesse no está configurada en el servidor (BACKUP_BIESSE_DATASOURCE_URL)");
        }

        BackupConfig config = ensureConfigRow();
        config.setEnabled(Boolean.TRUE.equals(request.enabled()));
        config.setIntervalHours(request.intervalHours());
        config.setScheduledHour(request.scheduledHour());
        config.setSaveToFolder(Boolean.TRUE.equals(request.saveToFolder()));
        config.setSendByEmail(Boolean.TRUE.equals(request.sendByEmail()));
        config.setEmailRecipients(normalizeRecipients(request.emailRecipients()));
        config.setIncludeBiesseDb(Boolean.TRUE.equals(request.includeBiesseDb()));
        config.setRetentionCount(request.retentionCount());
        configRepository.save(config);
        return getConfig();
    }

    @Transactional(readOnly = true)
    public List<BackupRunDto> listHistory() {
        return runRepository.findTop50ByOrderByStartedAtDesc().stream()
                .map(run -> BackupRunDto.from(run, this::isFileDownloadable))
                .toList();
    }

    public BackupRunDto runManual() {
        return executeBackup("MANUAL");
    }

    public void runScheduledIfDue() {
        BackupConfig config = ensureConfigRow();
        if (!config.isEnabled()) {
            return;
        }
        if (!isDue(config)) {
            return;
        }
        try {
            executeBackup("SCHEDULED");
        } catch (Exception ex) {
            log.error("Backup programado falló: {}", ex.getMessage(), ex);
        }
    }

    public Resource resolveDownloadFile(Long runId, String filename) {
        if (filename == null || !SAFE_FILENAME.matcher(filename).matches()) {
            throw new BadRequestException("Nombre de archivo no válido");
        }
        BackupRun run = runRepository
                .findById(runId)
                .orElseThrow(() -> new BadRequestException("Backup no encontrado"));
        if (run.getFileNames() == null || !run.getFileNames().contains(filename)) {
            throw new BadRequestException("El archivo no pertenece a ese backup");
        }
        Path file = storageRoot().resolve(filename);
        if (!Files.isRegularFile(file)) {
            throw new BadRequestException("Archivo no encontrado en el servidor");
        }
        return new FileSystemResource(file);
    }

    private BackupRunDto executeBackup(String triggerType) {
        if (!running.compareAndSet(false, true)) {
            throw new BadRequestException("Ya hay un backup en ejecución");
        }
        BackupConfig config = ensureConfigRow();
        BackupRun run = new BackupRun();
        run.setStartedAt(Instant.now());
        run.setStatus("RUNNING");
        run.setTriggerType(triggerType);
        run.setEmailed(false);
        run = runRepository.save(run);

        List<String> createdFiles = new ArrayList<>();
        java.util.Map<String, byte[]> dumpByFile = new java.util.LinkedHashMap<>();
        long totalBytes = 0L;
        boolean emailed = false;
        String errorMessage = null;

        try {
            if (!isPgDumpAvailable()) {
                throw new BadRequestException(
                        "pg_dump no está disponible en el servidor. Instale postgresql-client.");
            }
            ensureStorageDirectory();
            String stamp = STAMP.format(LocalDateTime.now());

            byte[] systemDump = dumpDatabase(
                    JdbcUrlParser.parse(systemJdbcUrl), systemUsername, systemPassword);
            String systemFile = persistDump("app_db", systemDump, config.isSaveToFolder(), stamp);
            createdFiles.add(systemFile);
            dumpByFile.put(systemFile, systemDump);
            totalBytes += systemDump.length;

            if (config.isIncludeBiesseDb() && isBiesseConfigured()) {
                JdbcUrlParser.ConnectionInfo biesse = JdbcUrlParser.parse(backupProperties.biesseUrl());
                byte[] biesseDump = dumpDatabase(biesse, backupProperties.biesseUsername(), backupProperties.biessePassword());
                String biesseFile = persistDump("obras", biesseDump, config.isSaveToFolder(), stamp);
                createdFiles.add(biesseFile);
                dumpByFile.put(biesseFile, biesseDump);
                totalBytes += biesseDump.length;
            }

            if (config.isSendByEmail()) {
                emailed = sendBackupEmail(config, dumpByFile, totalBytes, run.getStartedAt());
            }

            if (config.isSaveToFolder()) {
                pruneOldBackups(config.getRetentionCount());
            }

            run.setStatus("SUCCESS");
            run.setMessage("Backup completado");
            config.setLastSuccessfulRunAt(Instant.now());
            configRepository.save(config);
        } catch (Exception ex) {
            errorMessage = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            run.setStatus("FAILED");
            run.setMessage(errorMessage);
            log.error("Backup {} falló: {}", triggerType, errorMessage, ex);
            if (ex instanceof BadRequestException bad) {
                throw bad;
            }
            throw new BadRequestException("No se pudo completar el backup: " + errorMessage);
        } finally {
            run.setFinishedAt(Instant.now());
            run.setFileNames(String.join(",", createdFiles));
            run.setTotalBytes(totalBytes > 0 ? totalBytes : null);
            run.setEmailed(emailed);
            runRepository.save(run);
            running.set(false);
        }

        return BackupRunDto.from(run, this::isFileDownloadable);
    }

    private boolean sendBackupEmail(
            BackupConfig config,
            java.util.Map<String, byte[]> dumpByFile,
            long totalBytes,
            Instant startedAt) {
        List<String> recipients = parseRecipients(config.getEmailRecipients());
        if (recipients.isEmpty()) {
            return false;
        }

        long maxBytes = (long) backupProperties.maxAttachmentMb() * 1024L * 1024L;
        boolean attach = totalBytes <= maxBytes;
        String stamp = STAMP.format(LocalDateTime.ofInstant(startedAt, ZoneId.systemDefault()));
        String subject = "Backup AllCenter — " + stamp;

        StringBuilder html = new StringBuilder();
        html.append("<p>Se generó un backup de la base de datos AllCenter.</p>");
        html.append("<p><strong>Fecha:</strong> ").append(stamp).append("</p>");
        if (!dumpByFile.isEmpty()) {
            html.append("<p><strong>Archivos:</strong> ")
                    .append(String.join(", ", dumpByFile.keySet()))
                    .append("</p>");
        }
        if (attach) {
            html.append("<p>Los archivos van adjuntos a este correo.</p>");
        } else {
            html.append("<p>Los archivos superan el límite de adjunto (")
                    .append(backupProperties.maxAttachmentMb())
                    .append(" MB). Descárguelos desde Gestión → Backups en el portal.</p>");
        }

        List<MailAttachment> attachments = new ArrayList<>();
        if (attach) {
            for (var entry : dumpByFile.entrySet()) {
                attachments.add(new MailAttachment(entry.getKey(), entry.getValue(), "application/gzip"));
            }
        }

        for (String recipient : recipients) {
            if (attach) {
                mailService.sendHtmlWithAttachments(recipient, subject, html.toString(), attachments);
            } else {
                mailService.sendHtml(recipient, subject, html.toString());
            }
        }
        return true;
    }

    private String persistDump(String dbLabel, byte[] gzipBytes, boolean saveToFolder, String stamp)
            throws IOException {
        String fileName = dbLabel + "_" + stamp + ".sql.gz";
        if (saveToFolder) {
            Files.write(storageRoot().resolve(fileName), gzipBytes);
        }
        return fileName;
    }

    private byte[] dumpDatabase(JdbcUrlParser.ConnectionInfo info, String username, String password)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(backupProperties.pgDumpPath());
        command.add("-h");
        command.add(info.host());
        command.add("-p");
        command.add(String.valueOf(info.port()));
        command.add("-U");
        command.add(username);
        command.add("-d");
        command.add(info.database());
        command.add("--no-owner");
        command.add("--no-acl");
        command.add("-F");
        command.add("p");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        if (StringUtils.hasText(password)) {
            pb.environment().put("PGPASSWORD", password);
        }

        Process process = pb.start();
        byte[] plainSql;
        try (InputStream in = process.getInputStream()) {
            plainSql = in.readAllBytes();
        }
        int code = process.waitFor();
        if (code != 0) {
            String detail = new String(plainSql);
            if (detail.length() > 500) {
                detail = detail.substring(0, 500);
            }
            throw new BadRequestException("pg_dump falló (" + info.database() + "): " + detail.trim());
        }

        ByteArrayOutputStream gzipOut = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(gzipOut)) {
            gos.write(plainSql);
        }
        return gzipOut.toByteArray();
    }

    private void pruneOldBackups(int retentionCount) throws IOException {
        Path root = storageRoot();
        if (!Files.isDirectory(root)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> stream = Files.list(root)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".sql.gz"))
                    .sorted(Comparator.comparing(p -> {
                        try {
                            return Files.getLastModifiedTime(p);
                        } catch (IOException ex) {
                            return null;
                        }
                    }, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        }
        for (int i = retentionCount; i < files.size(); i++) {
            Files.deleteIfExists(files.get(i));
        }
    }

    private boolean isDue(BackupConfig config) {
        Instant last = config.getLastSuccessfulRunAt();
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.now(zone);

        if (config.getIntervalHours() >= 24) {
            if (now.getHour() != config.getScheduledHour()) {
                return false;
            }
            if (last == null) {
                return true;
            }
            long elapsedHours =
                    (Instant.now().toEpochMilli() - last.toEpochMilli()) / (1000L * 60L * 60L);
            return elapsedHours >= config.getIntervalHours();
        }

        if (last == null) {
            return true;
        }
        long elapsedHours = (Instant.now().toEpochMilli() - last.toEpochMilli()) / (1000L * 60L * 60L);
        return elapsedHours >= config.getIntervalHours();
    }

    private BackupConfig ensureConfigRow() {
        return configRepository.findById(CONFIG_ID).orElseGet(() -> {
            BackupConfig config = new BackupConfig();
            config.setId(CONFIG_ID);
            return configRepository.save(config);
        });
    }

    private void ensureStorageDirectory() {
        try {
            Files.createDirectories(storageRoot());
        } catch (IOException ex) {
            throw new BadRequestException("No se pudo crear la carpeta de backups: " + ex.getMessage());
        }
    }

    private Path storageRoot() {
        return Paths.get(backupProperties.storageRoot()).toAbsolutePath().normalize();
    }

    private boolean isBiesseConfigured() {
        return StringUtils.hasText(backupProperties.biesseUrl())
                && StringUtils.hasText(backupProperties.biesseUsername());
    }

    private boolean isPgDumpAvailable() {
        if (pgDumpAvailable != null) {
            return pgDumpAvailable;
        }
        try {
            Process process = new ProcessBuilder(backupProperties.pgDumpPath(), "--version")
                    .redirectErrorStream(true)
                    .start();
            int code = process.waitFor();
            pgDumpAvailable = code == 0;
        } catch (Exception ex) {
            pgDumpAvailable = false;
        }
        return pgDumpAvailable;
    }

    private static List<String> parseRecipients(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Stream.of(raw.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private boolean isFileDownloadable(String filename) {
        if (filename == null || !SAFE_FILENAME.matcher(filename).matches()) {
            return false;
        }
        return Files.isRegularFile(storageRoot().resolve(filename));
    }

    private static String normalizeRecipients(String raw) {
        return String.join(", ", parseRecipients(raw));
    }
}
