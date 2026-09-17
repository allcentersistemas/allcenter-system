package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.config.BackupProperties;
import com.allcenter.modulesystem.dto.BackupRunDto;
import com.allcenter.modulesystem.exception.BadRequestException;
import com.allcenter.modulesystem.model.BackupRun;
import com.allcenter.modulesystem.repository.BackupRunRepository;
import com.allcenter.modulesystem.util.JdbcUrlParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackupRestoreService {

    static final String CONFIRM_TEXT = "RESTAURAR";
    private static final Pattern SAFE_FILENAME = Pattern.compile("^[a-zA-Z0-9._-]+\\.(sql\\.gz|zip)$");

    private final BackupRunRepository runRepository;
    private final BackupProperties backupProperties;
    private final BackupService backupService;
    private final MediaBackupService mediaBackupService;

    @Value("${spring.datasource.url}")
    private String systemJdbcUrl;

    @Value("${spring.datasource.username}")
    private String systemUsername;

    @Value("${spring.datasource.password}")
    private String systemPassword;

    private final AtomicBoolean restoreRunning = new AtomicBoolean(false);

    public List<BackupRunDto> listRestoreHistory() {
        return runRepository.findTop50ByOrderByStartedAtDesc().stream()
                .filter(r -> r.getTriggerType() != null && r.getTriggerType().startsWith("RESTORE"))
                .map(run -> BackupRunDto.from(run, backupService::isFileDownloadable))
                .toList();
    }

    public BackupRunDto startRestoreFromHistory(Long runId, String filename, String confirmText) {
        requireConfirm(confirmText);
        if (filename != null && filename.startsWith(MediaBackupService.MEDIA_ZIP_PREFIX)) {
            throw new BadRequestException(
                    "Para restaurar archivos use la opción Restaurar solo archivos");
        }
        if (!restoreRunning.compareAndSet(false, true)) {
            throw new BadRequestException("Ya hay una restauración en ejecución");
        }
        if (!isPsqlAvailable()) {
            restoreRunning.set(false);
            throw new BadRequestException("psql no está disponible en el servidor");
        }
        backupService.resolveDownloadFile(runId, filename);
        Path source = backupService.storageRoot().resolve(filename);
        if (!Files.isRegularFile(source)) {
            restoreRunning.set(false);
            throw new BadRequestException("Archivo de backup no encontrado en el servidor");
        }
        String trigger = resolveTriggerType(filename);
        BackupRun run = createRunningRestore(trigger, filename);
        Path copy = source;
        CompletableFuture.runAsync(() -> performRestore(run.getId(), copy, filename));
        return BackupRunDto.from(run, backupService::isFileDownloadable);
    }

    public BackupRunDto startRestoreUpload(MultipartFile file, String confirmText) {
        requireConfirm(confirmText);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Seleccione un archivo .sql.gz o .zip");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        if (original.startsWith(MediaBackupService.MEDIA_ZIP_PREFIX)) {
            throw new BadRequestException(
                    "Para restaurar archivos use Gestión → Backups → Restaurar solo archivos");
        }
        if (!SAFE_FILENAME.matcher(original).matches()) {
            throw new BadRequestException("Solo se admiten archivos .sql.gz o .zip");
        }
        if (!restoreRunning.compareAndSet(false, true)) {
            throw new BadRequestException("Ya hay una restauración en ejecución");
        }
        if (!isPsqlAvailable()) {
            restoreRunning.set(false);
            throw new BadRequestException("psql no está disponible en el servidor");
        }
        try {
            Path uploadDir = Files.createDirectories(backupService.storageRoot().resolve("uploads"));
            String safeName = "upload_" + System.currentTimeMillis() + "_" + original.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path target = uploadDir.resolve(safeName);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
            String trigger = BackupService.isCompleteZipName(original)
                    ? "RESTORE_COMPLETE"
                    : original.endsWith(".zip") ? "RESTORE_UPLOAD_ZIP" : resolveTriggerType(original);
            BackupRun run = createRunningRestore(trigger, original);
            CompletableFuture.runAsync(() -> performRestore(run.getId(), target, original));
            return BackupRunDto.from(run, backupService::isFileDownloadable);
        } catch (IOException ex) {
            restoreRunning.set(false);
            throw new BadRequestException("No se pudo guardar el archivo: " + ex.getMessage());
        }
    }

    private void performRestore(Long runId, Path sourceFile, String displayName) {
        BackupRun run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            restoreRunning.set(false);
            return;
        }
        Path tempDir = null;
        try {
            updateProgress(run, 5, "Preparando restauración…");
            tempDir = Files.createTempDirectory("allcenter-restore-");
            ExtractedBackup extracted = extractArchive(sourceFile, tempDir);
            List<Path> gzipFiles = new ArrayList<>(extracted.sqlGzips());
            if (gzipFiles.isEmpty()) {
                throw new BadRequestException("No se encontraron archivos .sql.gz en el backup");
            }
            gzipFiles.sort(Comparator.comparing(p -> p.getFileName().toString()));
            int dbShare = extracted.mediaZip() != null ? 70 : 85;
            int step = Math.max(1, dbShare / gzipFiles.size());
            int progress = 10;
            for (Path gzipFile : gzipFiles) {
                String name = gzipFile.getFileName().toString();
                String target = resolveTargetLabel(name);
                updateProgress(run, progress, "Limpiando esquema y restaurando " + target + "…");
                Path sqlFile = decompressGzip(gzipFile, tempDir);
                restoreGzipToDatabase(name, sqlFile);
                progress += step;
            }
            if (extracted.mediaZip() != null) {
                updateProgress(run, 88, "Restaurando cotizaciones y archivos RM…");
                mediaBackupService.restoreMediaArchive(extracted.mediaZip());
            }
            run.setStatus("SUCCESS");
            run.setMessage(
                    "Restauración completada desde "
                            + displayName
                            + (extracted.mediaZip() != null ? " (bases + archivos)" : "")
                            + ". Reinicie el backend para alinear esquema y conexiones.");
            updateProgress(run, 100, "Completado");
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            run.setStatus("FAILED");
            run.setMessage(msg);
            run.setProgressStage("Error");
            log.error("Restauración falló: {}", msg, ex);
        } finally {
            run.setFinishedAt(Instant.now());
            run.setFileNames(displayName);
            runRepository.save(run);
            restoreRunning.set(false);
            if (tempDir != null) {
                deleteRecursive(tempDir);
            }
            if (displayName.startsWith("upload_")) {
                try {
                    Files.deleteIfExists(sourceFile);
                } catch (IOException ex) {
                    log.warn("No se pudo borrar upload temporal: {}", ex.getMessage());
                }
            }
        }
    }

    private record ExtractedBackup(List<Path> sqlGzips, Path mediaZip) {}

    private ExtractedBackup extractArchive(Path source, Path tempDir) throws IOException {
        String name = source.getFileName().toString().toLowerCase();
        if (name.endsWith(".sql.gz")) {
            return new ExtractedBackup(List.of(source), null);
        }
        if (name.endsWith(".zip")) {
            List<Path> sql = new ArrayList<>();
            Path media = null;
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(source))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String entryName = Path.of(entry.getName()).getFileName().toString();
                    if (entryName.contains("..")) {
                        continue;
                    }
                    if (entryName.endsWith(".sql.gz")) {
                        Path out = tempDir.resolve(entryName);
                        Files.copy(zis, out);
                        sql.add(out);
                    } else if (entryName.startsWith(MediaBackupService.MEDIA_ZIP_PREFIX)
                            && entryName.endsWith(".zip")) {
                        Path out = tempDir.resolve(entryName);
                        Files.copy(zis, out);
                        media = out;
                    }
                }
            }
            return new ExtractedBackup(sql, media);
        }
        throw new BadRequestException("Formato no soportado. Use .sql.gz, .zip o allcenter_backup_*.zip");
    }

    private Path decompressGzip(Path gzipFile, Path tempDir) throws IOException {
        String baseName = gzipFile.getFileName().toString();
        String sqlName = baseName.endsWith(".gz") ? baseName.substring(0, baseName.length() - 3) : baseName + ".sql";
        Path sqlFile = tempDir.resolve(sqlName);
        try (GZIPInputStream gis = new GZIPInputStream(Files.newInputStream(gzipFile));
                OutputStream out = Files.newOutputStream(sqlFile)) {
            gis.transferTo(out);
        }
        return sqlFile;
    }

    private void restoreGzipToDatabase(String gzipFileName, Path sqlFile)
            throws IOException, InterruptedException {
        if (gzipFileName.contains("obras_")) {
            if (!StringUtils.hasText(backupProperties.biesseUrl())) {
                throw new BadRequestException(
                        "Backup obras_*.sql.gz requiere BACKUP_BIESSE_DATASOURCE_URL");
            }
            JdbcUrlParser.ConnectionInfo info = JdbcUrlParser.parse(backupProperties.biesseUrl());
            prepareEmptyPublicSchema(info, backupProperties.biesseUsername(), backupProperties.biessePassword());
            runPsql(info, backupProperties.biesseUsername(), backupProperties.biessePassword(), sqlFile);
            return;
        }
        if (gzipFileName.contains("app_db_")) {
            JdbcUrlParser.ConnectionInfo info = JdbcUrlParser.parse(systemJdbcUrl);
            prepareEmptyPublicSchema(info, systemUsername, systemPassword);
            runPsql(info, systemUsername, systemPassword, sqlFile);
            return;
        }
        throw new BadRequestException(
                "No se reconoce la base del archivo "
                        + gzipFileName
                        + ". Use app_db_*.sql.gz u obras_*.sql.gz");
    }

    private void prepareEmptyPublicSchema(
            JdbcUrlParser.ConnectionInfo info, String username, String password)
            throws IOException, InterruptedException {
        String sql =
                "DROP SCHEMA IF EXISTS public CASCADE; "
                        + "CREATE SCHEMA public; "
                        + "GRANT ALL ON SCHEMA public TO CURRENT_USER; "
                        + "GRANT ALL ON SCHEMA public TO public;";
        runPsqlCommand(info, username, password, sql);
    }

    private void runPsql(
            JdbcUrlParser.ConnectionInfo info, String username, String password, Path sqlFile)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(backupProperties.psqlPath());
        command.add("-h");
        command.add(info.host());
        command.add("-p");
        command.add(String.valueOf(info.port()));
        command.add("-U");
        command.add(username);
        command.add("-d");
        command.add(info.database());
        command.add("-v");
        command.add("ON_ERROR_STOP=1");
        command.add("-f");
        command.add(sqlFile.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        if (StringUtils.hasText(password)) {
            pb.environment().put("PGPASSWORD", password);
        }
        Process process = pb.start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes());
        }
        int code = process.waitFor();
        if (code != 0) {
            String detail = output.length() > 800 ? output.substring(output.length() - 800) : output;
            throw new BadRequestException(
                    "psql falló (" + info.database() + "): " + detail.trim());
        }
    }

    private void runPsqlCommand(
            JdbcUrlParser.ConnectionInfo info, String username, String password, String sql)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(backupProperties.psqlPath());
        command.add("-h");
        command.add(info.host());
        command.add("-p");
        command.add(String.valueOf(info.port()));
        command.add("-U");
        command.add(username);
        command.add("-d");
        command.add(info.database());
        command.add("-v");
        command.add("ON_ERROR_STOP=1");
        command.add("-c");
        command.add(sql);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        if (StringUtils.hasText(password)) {
            pb.environment().put("PGPASSWORD", password);
        }
        Process process = pb.start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes());
        }
        int code = process.waitFor();
        if (code != 0) {
            String detail = output.length() > 800 ? output.substring(output.length() - 800) : output;
            throw new BadRequestException("psql falló (" + info.database() + "): " + detail.trim());
        }
    }

    private BackupRun createRunningRestore(String triggerType, String sourceLabel) {
        BackupRun run = new BackupRun();
        run.setStartedAt(Instant.now());
        run.setStatus("RUNNING");
        run.setTriggerType(triggerType);
        run.setEmailed(false);
        run.setProgressPercent(0);
        run.setProgressStage("Iniciando restauración…");
        run.setFileNames(sourceLabel);
        return runRepository.save(run);
    }

    private void updateProgress(BackupRun run, int percent, String stage) {
        run.setProgressPercent(Math.min(100, Math.max(0, percent)));
        run.setProgressStage(stage);
        runRepository.save(run);
    }

    private static void requireConfirm(String confirmText) {
        if (confirmText == null || !CONFIRM_TEXT.equals(confirmText.trim())) {
            throw new BadRequestException("Escriba " + CONFIRM_TEXT + " para confirmar la restauración");
        }
    }

    private static String resolveTriggerType(String filename) {
        if (BackupService.isCompleteZipName(filename)) {
            return "RESTORE_COMPLETE";
        }
        if (filename != null && filename.contains("obras_")) {
            return "RESTORE_BIESSE";
        }
        return "RESTORE_SYSTEM";
    }

    private static String resolveTargetLabel(String filename) {
        if (filename != null && filename.contains("obras_")) {
            return "obras (Biesse)";
        }
        if (filename != null && filename.contains("app_db_")) {
            return "app_db";
        }
        return filename;
    }

    private boolean isPsqlAvailable() {
        try {
            Process process = new ProcessBuilder(backupProperties.psqlPath(), "--version")
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private static void deleteRecursive(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
