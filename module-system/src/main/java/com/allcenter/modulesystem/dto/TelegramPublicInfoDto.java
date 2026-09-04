package com.allcenter.modulesystem.dto;

/**
 * Info pública del bot (sin token) para registro / perfil del portal cliente.
 */
public record TelegramPublicInfoDto(
        boolean enabled, String botUsername, String botUrl) {

    public static TelegramPublicInfoDto disabled() {
        return new TelegramPublicInfoDto(false, null, null);
    }

    public static TelegramPublicInfoDto of(boolean enabled, String botUsername) {
        String user = AppConfigDto.normalizeBotUsername(botUsername);
        if (!enabled || user == null) {
            return new TelegramPublicInfoDto(false, user, null);
        }
        return new TelegramPublicInfoDto(true, user, "https://t.me/" + user);
    }
}
