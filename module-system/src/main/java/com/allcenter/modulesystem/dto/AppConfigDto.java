package com.allcenter.modulesystem.dto;

import com.allcenter.modulesystem.model.AppConfig;
import java.time.LocalDate;

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
        boolean telegramBotTokenConfigured,
        String telegramBotUsername,
        boolean whatsappEnabled,
        boolean whatsappAccessTokenConfigured,
        String whatsappPhoneNumberId,
        /** ISO yyyy-MM-dd — fecha de inicio del tablero Seguimiento. */
        String seguimientoSince) {

    public static AppConfigDto from(AppConfig config) {
        LocalDate since =
                config.getSeguimientoSince() != null
                        ? config.getSeguimientoSince()
                        : LocalDate.of(2026, 9, 9);
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
                config.getTelegramBotToken() != null && !config.getTelegramBotToken().isBlank(),
                normalizeBotUsername(config.getTelegramBotUsername()),
                config.isWhatsappEnabled(),
                config.getWhatsappAccessToken() != null && !config.getWhatsappAccessToken().isBlank(),
                blankToNull(config.getWhatsappPhoneNumberId()),
                since.toString());
    }

    /** Usuario sin @; null si vacío. */
    public static String normalizeBotUsername(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String u = raw.trim();
        if (u.startsWith("@")) {
            u = u.substring(1).trim();
        }
        return u.isEmpty() ? null : u;
    }

    private static String blankToNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }
}
