package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.NotBlank;

public record BackupRestoreFromHistoryRequest(
        @NotBlank String confirmText,
        Long runId,
        @NotBlank String filename,
        /** Si true, el media sobrescribe archivos existentes. Por defecto solo añade. */
        Boolean overwriteMedia) {}
