package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.BackupRunDto;
import com.allcenter.modulesystem.exception.BadRequestException;
import com.allcenter.modulesystem.model.BackupRun;
import com.allcenter.modulesystem.repository.BackupRunRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaBackupRestoreService {

    static final String CONFIRM_TEXT = "RESTAURAR";
    private static final Pattern SAFE_MEDIA_UPLOAD =
            Pattern.compile("^(media_files_[a-zA-Z0-9._-]+\\.zip|.+\\.zip)$");
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    private final BackupRunRepository runRepository;
    private final BackupService backupService;
    private final MediaBackupService mediaBackupService;

    private final AtomicBoolean mediaBackupRunning = new AtomicBoolean(false);
    private final AtomicBoolean mediaRestoreRunning = new AtomicBoolean(false);

    public BackupRunDto startManualMediaBackup() {
        if (!mediaBackupRunning.compareAndSet(false, true)) {
            throw new BadRequestException("Ya hay un backup de archivos en ejecución");
        }
        BackupRun run = createRunningRun("MANUAL_FILES");
        CompletableFuture.runAsync(() -> performMediaBackup(run.getId()));
        return BackupRunDto.from(run, backupService::isFileDownloadable);
    }

    public BackupRunDto startRestoreMediaFromHistory(Long runId, String filename, String confirmText) {
        requireConfirm(confirmText);
        if (!mediaBackupService.isMediaZipName(filename)) {
            throw new BadRequestException("Solo se pueden restaurar archivos media_files_*.zip desde aquí");
        }
        if (!mediaRestoreRunning.compareAndSet(false, true)) {
            throw new BadRequestException("Ya hay una restauración de archivos en ejecución");
        }
        backupService.resolveDownloadFile(runId, filename);
        Path source = backupService.storageRoot().resolve(filename);
        if (!Files.isRegularFile(source)) {
            mediaRestoreRunning.set(false);
            throw new BadRequestException("Archivo de backup no encontrado en el servidor");
        }
        BackupRun run = createRunningRestore("RESTORE_MEDIA", filename);
        CompletableFuture.runAsync(() -> performMediaRestore(run.getId(), source, filename));
        return BackupRunDto.from(run, backupService::isFileDownloadable);
    }

    public BackupRunDto startRestoreMediaUpload(MultipartFile file, String confirmText) {
        requireConfirm(confirmText);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Seleccione un archivo .zip de archivos");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        if (!SAFE_MEDIA_UPLOAD.matcher(original).matches()) {
            throw new BadRequestException("Solo se admiten archivos .zip generados por el backup de archivos");
        }
        if (!mediaRestoreRunning.compareAndSet(false, true)) {
            throw new BadRequestException("Ya hay una restauración de archivos en ejecución");
        }
        try {
            Path uploadDir = Files.createDirectories(backupService.storageRoot().resolve("uploads"));
            String safeName =
                    "media_upload_" + System.currentTimeMillis() + "_" + original.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path target = uploadDir.resolve(safeName);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
            BackupRun run = createRunningRestore("RESTORE_MEDIA_UPLOAD", original);
            CompletableFuture.runAsync(() -> performMediaRestore(run.getId(), target, original));
            return BackupRunDto.from(run, backupService::isFileDownloadable);
        } catch (IOException ex) {
            mediaRestoreRunning.set(false);
            throw new BadRequestException("No se pudo guardar el archivo: " + ex.getMessage());
        }
    }

    void performMediaBackup(Long runId) {
        BackupRun run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            mediaBackupRunning.set(false);
            return;
        }
        try {
            updateProgress(run, 10, "Preparando backup de archivos…");
            backupService.ensureStorageDirectoryPublic();
            String stamp = STAMP.format(LocalDateTime.now());
            updateProgress(run, 35, "Comprimiendo cotizaciones y archivos RM…");
            long size = mediaBackupService.createMediaArchive(backupService.storageRoot(), stamp);
            String fileName = mediaBackupService.mediaZipFileName(stamp);
            updateProgress(run, 90, "Finalizando…");
            run.setStatus("SUCCESS");
            run.setMessage("Backup de archivos completado (" + fileName + ")");
            run.setFileNames(fileName);
            run.setTotalBytes(size);
            updateProgress(run, 100, "Completado");
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            run.setStatus("FAILED");
            run.setMessage(message);
            run.setProgressStage("Error");
            log.error("Backup de archivos falló: {}", message, ex);
        } finally {
            run.setFinishedAt(Instant.now());
            runRepository.save(run);
            mediaBackupRunning.set(false);
        }
    }

    private void performMediaRestore(Long runId, Path sourceFile, String displayName) {
        BackupRun run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            mediaRestoreRunning.set(false);
            return;
        }
        try {
            updateProgress(run, 10, "Preparando restauración de archivos…");
            updateProgress(run, 40, "Extrayendo cotizaciones y archivos RM…");
            mediaBackupService.restoreMediaArchive(sourceFile);
            run.setStatus("SUCCESS");
            run.setMessage(
                    "Archivos restaurados desde "
                            + displayName
                            + " (cotizaciones → "
                            + mediaBackupService.optimizacionMediaRoot()
                            + ", RM → "
                            + mediaBackupService.rmMediaRoot()
                            + ")");
            updateProgress(run, 100, "Completado");
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            run.setStatus("FAILED");
            run.setMessage(message);
            run.setProgressStage("Error");
            log.error("Restauración de archivos falló: {}", message, ex);
        } finally {
            run.setFinishedAt(Instant.now());
            run.setFileNames(displayName);
            runRepository.save(run);
            mediaRestoreRunning.set(false);
            if (displayName.startsWith("media_upload_") || sourceFile.getFileName().toString().startsWith("media_upload_")) {
                try {
                    Files.deleteIfExists(sourceFile);
                } catch (IOException ex) {
                    log.warn("No se pudo borrar upload temporal: {}", ex.getMessage());
                }
            }
        }
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

    private BackupRun createRunningRestore(String triggerType, String sourceLabel) {
        BackupRun run = new BackupRun();
        run.setStartedAt(Instant.now());
        run.setStatus("RUNNING");
        run.setTriggerType(triggerType);
        run.setEmailed(false);
        run.setProgressPercent(0);
        run.setProgressStage("Iniciando restauración de archivos…");
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
}
