package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TelegramTestRequest(
        @NotBlank @Size(max = 64) String chatId) {}
