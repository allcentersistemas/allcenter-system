package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClientCreateRequest(
        @NotBlank @Email String email,
        @Size(min = 3, max = 64) String username,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 180) String displayName,
        @Size(max = 40) String phone,
        @Size(max = 64) String telegramChatId,
        Boolean active) {}
