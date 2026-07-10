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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.context.annotation.DependsOn;

@Service
@RequiredArgsConstructor
@Slf4j
@DependsOn("backupConfigSchemaAligner")
public class BackupService {

    private static final long CONFIG_ID = 1L;
    private static final Pattern SAFE_SQL_FILENAME = Pattern.compile("^[a-zA-Z0-9._-]+\\.sql\\.gz$");
    private static final Pattern SAFE_MEDIA_FILENAME =
            Pattern.compile("^media_files_[a-zA-Z0-9._-]+\\.zip$");
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    private final BackupConfigRepository configRepository;
    private final BackupRunRepository runRepository;
    private final BackupProperties backupProperties;
    private final MailService mailService;
    private final AppConfigService appConfigService;
    private final MediaBackupService mediaBackupService;

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
                mediaBackupService.optimizacionMediaRoot().toString(),
                mediaBackupService.rmMediaRoot().toString(),
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
        config.setIncludeMediaFiles(Boolean.TRUE.equals(request.includeMediaFiles()));
        config.setRetentionCount(request.retentionCount());
        configRepository.save(config);
        return getConfig();
    }

    @Transactional(readOnly = true)
    public List<BackupRunDto> listHistory() {
        return runRepository.findTop50ByOrderByStartedAtDesc().stream()
                .filter(r -> r.getTriggerType() == null || !r.getTriggerType().startsWith("RESTORE"))
                .map(run -> BackupRunDto.from(run, this::isFileDownloadable))
                .toList();
    }

    @Transactional(readOnly = true)
    public BackupRunDto getRun(Long runId) {
        BackupRun run = runRepository
                .findById(runId)
                .orElseThrow(() -> new BadRequestException("Backup no encontrado"));
        return BackupRunDto.from(run, this::isFileDownloadable);
    }

    public BackupRunDto startManualBackup() {
        if (!running.compareAndSet(false, true)) {
            throw new BadRequestException("Ya hay un backup en ejecución");
        }
        if (!isPgDumpAvailable()) {
            running.set(false);
            throw new BadRequestException(
                    "pg_dump no está disponible en el servidor. Instale postgresql-client.");
        }
        BackupRun run = createRunningRun("MANUAL");
        Long runId = run.getId();
        CompletableFuture.runAsync(() -> performBackup(runId, "MANUAL"));
        return BackupRunDto.from(run, this::isFileDownloadable);
    }

    public void runScheduledIfDue() {
        BackupConfig config = ensureConfigRow();
        if (!config.isEnabled()) {
            return;
        }
        if (!isDue(config)) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("Backup programado omitido: ya hay uno en ejecución");
            return;
        }
        if (!isPgDumpAvailable()) {
            running.set(false);
            log.warn("Backup programado omitido: pg_dump no disponible");
            return;
        }
        BackupRun run = createRunningRun("SCHEDULED");
        CompletableFuture.runAsync(() -> performBackup(run.getId(), "SCHEDULED"));
    }

    public Resource resolveDownloadFile(Long runId, String filename) {
        if (filename == null || !isBackupFileName(filename)) {
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

    private BackupRun createRunningRun(String triggerType) {
        BackupRun run = new BackupRun();
        run.setStartedAt(Instant.now());
        run.setStatus("RUNNING");
        run.setTriggerType(triggerType);
        run.setEmailed(false);
        run.setProgressPercent(0);
        run.setProgressStage("Iniciando…");
        return runRepository.save(run);
    }

    private void performBackup(Long runId, String triggerType) {
        BackupRun run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            running.set(false);
            return;
        }

        BackupConfig config = ensureConfigRow();
        List<String> createdFiles = new ArrayList<>();
        java.util.Map<String, byte[]> dumpByFile = new java.util.LinkedHashMap<>();
        long totalBytes = 0L;
        boolean emailed = false;
        String emailError = null;
        String emailRecipientsSent = "";

        try {
            updateProgress(run, 5, "Preparando backup…");
            ensureStorageDirectory();
            String stamp = STAMP.format(LocalDateTime.now());

            updateProgress(run, 15, "Volcando base app_db…");
            byte[] systemDump = dumpDatabase(
                    JdbcUrlParser.parse(systemJdbcUrl), systemUsername, systemPassword);
            String systemFile = persistDump("app_db", systemDump, config.isSaveToFolder(), stamp);
            createdFiles.add(systemFile);
            dumpByFile.put(systemFile, systemDump);
            totalBytes += systemDump.length;
            updateProgress(run, config.isIncludeBiesseDb() && isBiesseConfigured() ? 40 : 55, "Base app_db lista");

            if (config.isIncludeBiesseDb() && isBiesseConfigured()) {
                updateProgress(run, 45, "Volcando base obras…");
                JdbcUrlParser.ConnectionInfo biesse = JdbcUrlParser.parse(backupProperties.biesseUrl());
                byte[] biesseDump =
                        dumpDatabase(biesse, backupProperties.biesseUsername(), backupProperties.biessePassword());
                String biesseFile = persistDump("obras", biesseDump, config.isSaveToFolder(), stamp);
                createdFiles.add(biesseFile);
                dumpByFile.put(biesseFile, biesseDump);
                totalBytes += biesseDump.length;
                updateProgress(run, 55, "Base obras lista");
            }

            if (config.isIncludeMediaFiles()) {
                updateProgress(run, 60, "Comprimiendo archivos (cotizaciones, RM)…");
                long mediaSize = mediaBackupService.createMediaArchive(storageRoot(), stamp);
                String mediaFile = mediaBackupService.mediaZipFileName(stamp);
                createdFiles.add(mediaFile);
                totalBytes += mediaSize;
                updateProgress(run, 63, "Archivos comprimidos");
            }

            if (config.isSaveToFolder()) {
                updateProgress(run, 65, "Limpiando copias antiguas…");
                pruneOldBackups(config.getRetentionCount());
            }

            if (config.isSendByEmail()) {
                updateProgress(run, 75, "Enviando correo…");
                try {
                    var emailResult = sendBackupEmail(config, createdFiles, dumpByFile, totalBytes, run.getStartedAt());
                    emailed = emailResult.sent();
                    emailRecipientsSent = emailResult.recipients();
                    if (!emailed) {
                        emailError = emailResult.detail();
                    }
                } catch (Exception mailEx) {
                    emailError = mailEx.getMessage() == null ? mailEx.getClass().getSimpleName() : mailEx.getMessage();
                    log.error("Backup {}: falló el envío de correo: {}", triggerType, emailError, mailEx);
                }
            }

            updateProgress(run, 95, "Finalizando…");
            run.setStatus("SUCCESS");
            if (emailError != null) {
                run.setMessage("Backup completado. Correo no enviado: " + emailError);
            } else if (config.isSendByEmail() && emailed) {
                run.setMessage(
                        "Backup completado. Correo enviado a: " + emailRecipientsSent + ". Revise spam si no lo ve.");
            } else {
                run.setMessage("Backup completado");
            }
            config.setLastSuccessfulRunAt(Instant.now());
            configRepository.save(config);
        } catch (Exception ex) {
            String errorMessage = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            run.setStatus("FAILED");
            run.setMessage(errorMessage);
            run.setProgressPercent(0);
            run.setProgressStage("Error");
            log.error("Backup {} falló: {}", triggerType, errorMessage, ex);
        } finally {
            run.setFinishedAt(Instant.now());
            run.setFileNames(String.join(",", createdFiles));
            run.setTotalBytes(totalBytes > 0 ? totalBytes : null);
            run.setEmailed(emailed);
            run.setEmailRecipientsSent(emailRecipientsSent);
            if ("SUCCESS".equals(run.getStatus())) {
                run.setProgressPercent(100);
                run.setProgressStage("Completado");
            }
            runRepository.save(run);
            running.set(false);
        }
    }

    private void updateProgress(BackupRun run, int percent, String stage) {
        run.setProgressPercent(Math.min(100, Math.max(0, percent)));
        run.setProgressStage(stage);
        runRepository.save(run);
    }

    private record BackupEmailResult(boolean sent, String recipients, String detail) {}

    private BackupEmailResult sendBackupEmail(
            BackupConfig config,
            List<String> createdFiles,
            java.util.Map<String, byte[]> dumpByFile,
            long totalBytes,
            Instant startedAt) throws IOException {
        List<String> recipients = parseRecipients(config.getEmailRecipients());
        if (recipients.isEmpty()) {
            throw new BadRequestException("No hay correos destino en la configuración de backups");
        }
        if (!appConfigService.isMailEnabled()) {
            throw new BadRequestException("El correo está desactivado en Configuración del portal");
        }

        String recipientList = String.join(", ", recipients);
        long maxBytes = (long) backupProperties.maxAttachmentMb() * 1024L * 1024L;
        boolean attach = totalBytes <= maxBytes && !createdFiles.isEmpty();
        String stamp = STAMP.format(LocalDateTime.ofInstant(startedAt, ZoneId.systemDefault()));
        String subject = "Backup AllCenter — " + stamp;

        String plain = "Se generó un backup de AllCenter (base de datos"
                + (config.isIncludeMediaFiles() ? " y archivos: cotizaciones + fotos RM" : "")
                + ").\n"
                + "Fecha: " + stamp + "\n"
                + "Archivos: " + String.join(", ", createdFiles) + "\n";
        StringBuilder html = new StringBuilder();
        html.append("<p>Se generó un backup de AllCenter (base de datos");
        if (config.isIncludeMediaFiles()) {
            html.append(" y archivos: cotizaciones + fotos RM");
        }
        html.append(").</p>");
        html.append("<p><strong>Fecha:</strong> ").append(stamp).append("</p>");
        if (!createdFiles.isEmpty()) {
            html.append("<p><strong>Archivos:</strong> ")
                    .append(String.join(", ", createdFiles))
                    .append("</p>");
        }

        Path zipPath = null;
        List<MailFileAttachment> fileAttachments = List.of();
        if (attach) {
            zipPath = createZipBundle(stamp, createdFiles, dumpByFile);
            long zipSize = Files.size(zipPath);
            String zipName = zipPath.getFileName().toString();
            plain += "Adjunto: " + zipName + " (" + formatSize(zipSize) + ")\n";
            html.append("<p>Adjunto: <strong>").append(zipName).append("</strong> (")
                    .append(formatSize(zipSize))
                    .append(").</p>");
            fileAttachments = List.of(new MailFileAttachment(zipName, zipPath, "application/zip"));
        } else {
            plain += "Los archivos superan el límite de adjunto ("
                    + backupProperties.maxAttachmentMb()
                    + " MB). Descárguelos desde Gestión → Backups.\n";
            html.append("<p>Los archivos superan el límite de adjunto (")
                    .append(backupProperties.maxAttachmentMb())
                    .append(" MB). Descárguelos desde Gestión → Backups en el portal.</p>");
        }
        plain += "\nSi no ve este correo, revise la carpeta de spam.";

        try {
            for (String recipient : recipients) {
                appConfigService.sendBackupNotification(
                        recipient, subject, plain, html.toString(), fileAttachments);
            }
            return new BackupEmailResult(true, recipientList, "Enviado a " + recipientList);
        } finally {
            if (zipPath != null) {
                Files.deleteIfExists(zipPath);
            }
        }
    }

    private Path createZipBundle(String stamp, List<String> fileNames, java.util.Map<String, byte[]> dumpByFile)
            throws IOException {
        String zipName = "allcenter_backup_" + stamp + ".zip";
        Path zipPath = storageRoot().resolve(zipName);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (String fileName : fileNames) {
                Path source = storageRoot().resolve(fileName);
                ZipEntry entry = new ZipEntry(fileName);
                zos.putNextEntry(entry);
                if (Files.isRegularFile(source)) {
                    Files.copy(source, zos);
                } else if (dumpByFile.containsKey(fileName)) {
                    zos.write(dumpByFile.get(fileName));
                }
                zos.closeEntry();
            }
        }
        return zipPath;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
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
        pruneFilesBySuffix(retentionCount, ".sql.gz");
        pruneFilesBySuffix(retentionCount, ".zip", MediaBackupService.MEDIA_ZIP_PREFIX);
    }

    private void pruneFilesBySuffix(int retentionCount, String suffix, String requiredPrefix) throws IOException {
        Path root = storageRoot();
        if (!Files.isDirectory(root)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> stream = Files.list(root)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        if (!name.endsWith(suffix)) {
                            return false;
                        }
                        return requiredPrefix == null || name.startsWith(requiredPrefix);
                    })
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

    private void pruneFilesBySuffix(int retentionCount, String suffix) throws IOException {
        pruneFilesBySuffix(retentionCount, suffix, null);
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

    void ensureStorageDirectoryPublic() {
        ensureStorageDirectory();
    }

    Path storageRoot() {
        return Paths.get(backupProperties.storageRoot()).toAbsolutePath().normalize();
    }

    private boolean isBackupFileName(String filename) {
        return SAFE_SQL_FILENAME.matcher(filename).matches()
                || SAFE_MEDIA_FILENAME.matcher(filename).matches();
    }

    boolean isFileDownloadable(String filename) {
        if (filename == null || !isBackupFileName(filename)) {
            return false;
        }
        return Files.isRegularFile(storageRoot().resolve(filename));
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

    private static String normalizeRecipients(String raw) {
        return String.join(", ", parseRecipients(raw));
    }
}
