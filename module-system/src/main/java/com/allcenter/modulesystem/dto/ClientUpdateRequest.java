package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.Size;

public record ClientUpdateRequest(
        @Size(max = 180) String displayName,
        @Size(max = 180) String companyName,
        @Size(max = 40) String phone,
        @Size(max = 40) String taxId,
        Boolean active) {}
