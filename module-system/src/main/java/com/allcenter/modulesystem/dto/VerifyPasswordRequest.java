package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyPasswordRequest(@NotBlank String password) {}
