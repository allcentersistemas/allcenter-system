package com.allcenter.moduleemployee.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
        @NotBlank
                @Size(max = 64)
                @Pattern(
                        regexp = "^[A-Za-z0-9_-]+$",
                        message = "only letters, digits, underscore and hyphen")
                String name,
        @Size(max = 500) String description) {}
