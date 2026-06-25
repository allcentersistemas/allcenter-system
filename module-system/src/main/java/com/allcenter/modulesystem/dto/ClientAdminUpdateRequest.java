package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record ClientAdminUpdateRequest(
        @Email @Size(max = 180) String email,
        @Size(min = 3, max = 64) String username,
        @Size(max = 180) String displayName,
        @Size(max = 40) String phone,
        @Size(max = 16) String tipoDocumento,
        @Size(max = 40) String numeroDocumento,
        @Size(max = 200) String direccion,
        @Size(max = 120) String ciudad,
        @Size(max = 120) String distrito,
        @Size(max = 120) String departamento,
        @Size(max = 180) String razonSocial,
        @Size(max = 20) String ruc,
        @Size(max = 180) String nombre,
        Boolean juridica,
        Boolean active) {}
