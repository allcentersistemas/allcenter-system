package com.allcenter.moduleemployee.model.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyPasswordRequest(@NotBlank String password) {}
