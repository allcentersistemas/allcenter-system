package com.allcenter.modulesystem.dto;

import com.allcenter.modulesystem.model.DocumentType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Crea el primer usuario (rol MASTER) cuando la base no tiene empleados. Solo una vez; después use
 * login.
 */
public record FirstSetupRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 120) String firstName,
        @NotBlank @Size(max = 120) String lastName,
        @NotNull DocumentType documentType,
        @NotBlank @Size(max = 64) String documentNumber) {}
