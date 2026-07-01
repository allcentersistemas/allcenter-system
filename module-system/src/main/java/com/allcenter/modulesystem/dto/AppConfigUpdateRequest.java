package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AppConfigUpdateRequest(
        Boolean kardexEnabled,
        Boolean mailEnabled,
        String mailFrom,
        String mailFromName,
        String smtpHost,
        @Min(1) @Max(65535) Integer smtpPort,
        String smtpUsername,
        /** Vacío o null = no cambiar contraseña almacenada. */
        String smtpPassword,
        Boolean smtpAuth,
        Boolean smtpStarttls) {}
