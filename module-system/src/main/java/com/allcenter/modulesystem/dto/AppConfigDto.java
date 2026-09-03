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
        boolean smtpStarttls,
        boolean aiVisionEnabled,
        String aiProvider,
        String aiModel,
        boolean aiApiKeyConfigured,
        int aiDailyLimitPerClient,
        boolean telegramEnabled,
        boolean telegramBotTokenConfigured) {

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
                config.isSmtpStarttls(),
                config.isAiVisionEnabled(),
                blankToNull(config.getAiProvider()) != null
                        ? config.getAiProvider().trim().toLowerCase()
                        : "claude",
                blankToNull(config.getAiModel()),
                config.getAiApiKey() != null && !config.getAiApiKey().isBlank(),
                Math.max(0, config.getAiDailyLimitPerClient()),
                config.isTelegramEnabled(),
                config.getTelegramBotToken() != null && !config.getTelegramBotToken().isBlank());
    }

    private static String blankToNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }
}
