package com.allcenter.modulesystem.dto;

/** Info pública para el portal cliente (sin secretos). */
public record WhatsAppPublicInfoDto(boolean enabled) {

    public static WhatsAppPublicInfoDto disabled() {
        return new WhatsAppPublicInfoDto(false);
    }

    public static WhatsAppPublicInfoDto of(boolean enabled) {
        return new WhatsAppPublicInfoDto(enabled);
    }
}
