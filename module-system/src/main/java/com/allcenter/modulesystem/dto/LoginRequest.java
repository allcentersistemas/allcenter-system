package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Empleados: {@code username} = usuario (samAccountName) o código EMP-*. Portal cliente: suele ser el email. */
public record LoginRequest(
        @NotBlank @Size(min = 1, max = 128) String username, @NotBlank String password) {}
