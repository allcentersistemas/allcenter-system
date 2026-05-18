package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminResetPasswordRequest(
        @NotBlank @Size(min = 8, max = 128) String newPassword,
        /** Si es true y el correo SMTP está activo, envía la nueva contraseña al email del empleado. */
        Boolean notifyByEmail) {}
