package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClientCreateRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 180) String displayName,
        @Size(max = 180) String companyName,
        @Size(max = 40) String phone,
        @Size(max = 40) String taxId,
        Boolean active) {}
