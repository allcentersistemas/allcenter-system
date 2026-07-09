package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BackupConfigUpdateRequest(
        @NotNull Boolean enabled,
        @Min(1) @Max(168) int intervalHours,
        @Min(0) @Max(23) int scheduledHour,
        @NotNull Boolean saveToFolder,
        @NotNull Boolean sendByEmail,
        String emailRecipients,
        @NotNull Boolean includeBiesseDb,
        @NotNull Boolean includeMediaFiles,
        @Min(1) @Max(100) int retentionCount) {}
