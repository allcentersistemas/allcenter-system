package com.allcenter.modulesystem.dto;

import com.allcenter.modulesystem.model.BackupConfig;
import java.time.Instant;

public record BackupConfigDto(
        boolean enabled,
        int intervalHours,
        int scheduledHour,
        boolean saveToFolder,
        boolean sendByEmail,
        String emailRecipients,
        boolean includeBiesseDb,
        boolean includeMediaFiles,
        int retentionCount,
        Instant lastSuccessfulRunAt,
        String storageRoot,
        String optimizacionMediaRoot,
        String rmMediaRoot,
        boolean mailAvailable,
        boolean pgDumpAvailable,
        boolean biesseConfigured) {

    public static BackupConfigDto from(
            BackupConfig config,
            String storageRoot,
            String optimizacionMediaRoot,
            String rmMediaRoot,
            boolean mailAvailable,
            boolean pgDumpAvailable,
            boolean biesseConfigured) {
        return new BackupConfigDto(
                config.isEnabled(),
                config.getIntervalHours(),
                config.getScheduledHour(),
                config.isSaveToFolder(),
                config.isSendByEmail(),
                config.getEmailRecipients() == null ? "" : config.getEmailRecipients(),
                config.isIncludeBiesseDb(),
                config.isIncludeMediaFiles(),
                config.getRetentionCount(),
                config.getLastSuccessfulRunAt(),
                storageRoot,
                optimizacionMediaRoot,
                rmMediaRoot,
                mailAvailable,
                pgDumpAvailable,
                biesseConfigured);
    }
}
