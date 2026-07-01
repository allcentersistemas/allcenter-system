package com.allcenter.modulesystem.dto;

import com.allcenter.modulesystem.model.AppConfig;

public record AppConfigDto(
        boolean kardexEnabled,
        boolean mailEnabled,
        String mailFrom,
        String mailFromName,
        String smtpHost,
        int smtpPort,
        String smtpUsername,
        boolean smtpPasswordConfigured,
        boolean smtpAuth,
        boolean smtpStarttls) {

    public static AppConfigDto from(AppConfig config) {
        return new AppConfigDto(
                config.isKardexEnabled(),
                config.isMailEnabled(),
                blankToNull(config.getMailFrom()),
                blankToNull(config.getMailFromName()),
                blankToNull(config.getSmtpHost()),
                config.getSmtpPort(),
                blankToNull(config.getSmtpUsername()),
                config.getSmtpPassword() != null && !config.getSmtpPassword().isBlank(),
                config.isSmtpAuth(),
                config.isSmtpStarttls());
    }

    private static String blankToNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }
}
