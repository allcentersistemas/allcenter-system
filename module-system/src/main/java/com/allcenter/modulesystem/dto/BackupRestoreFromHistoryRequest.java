package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.NotBlank;

public record BackupRestoreFromHistoryRequest(
        @NotBlank String confirmText, Long runId, @NotBlank String filename) {}
