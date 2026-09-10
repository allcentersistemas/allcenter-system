package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WhatsAppTestRequest(
        @NotBlank @Size(max = 32) String phone) {}
