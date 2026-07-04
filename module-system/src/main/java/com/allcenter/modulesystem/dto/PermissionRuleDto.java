package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PermissionRuleDto(
        @NotBlank @Size(max = 32) String action, @NotBlank @Size(max = 80) String subject) {}
