package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record MailTestRequest(@NotBlank @Email String to) {}
