package com.allcenter.modulesystem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateRoleRequest(
        @NotBlank
                @Size(max = 64)
                @Pattern(
                        regexp = "^[A-Za-z0-9_-]+$",
                        message = "only letters, digits, underscore and hyphen")
                String name,
        @Size(max = 500) String description,
        @Valid List<PermissionRuleDto> permissions) {}
