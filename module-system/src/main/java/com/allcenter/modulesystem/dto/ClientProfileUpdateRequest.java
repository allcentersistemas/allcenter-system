package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.Size;

/** Actualización de perfil por el propio cliente del portal. */
public record ClientProfileUpdateRequest(
        /** Vacío o null limpia el Chat ID. */
        @Size(max = 64) String telegramChatId) {}
