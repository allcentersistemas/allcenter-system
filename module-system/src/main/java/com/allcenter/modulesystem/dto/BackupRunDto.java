package com.allcenter.modulesystem.dto;

import com.allcenter.modulesystem.model.BackupRun;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public record BackupRunDto(
        Long id,
        Instant startedAt,
        Instant finishedAt,
        String status,
        String triggerType,
        String message,
        List<BackupFileDto> files,
        Long totalBytes,
        boolean emailed,
        int progressPercent,
        String progressStage,
        String emailRecipientsSent) {

    public record BackupFileDto(String name, boolean downloadable) {}

    public static BackupRunDto from(BackupRun run, java.util.function.Function<String, Boolean> downloadableCheck) {
        List<BackupFileDto> files = List.of();
        if (run.getFileNames() != null && !run.getFileNames().isBlank()) {
            files = Arrays.stream(run.getFileNames().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(name -> new BackupFileDto(
                            name,
                            downloadableCheck != null && Boolean.TRUE.equals(downloadableCheck.apply(name))))
                    .toList();
        }
        return new BackupRunDto(
                run.getId(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getStatus(),
                run.getTriggerType(),
                run.getMessage(),
                files,
                run.getTotalBytes(),
                run.isEmailed(),
                run.getProgressPercent(),
                run.getProgressStage() == null ? "" : run.getProgressStage(),
                run.getEmailRecipientsSent() == null ? "" : run.getEmailRecipientsSent());
    }
}
