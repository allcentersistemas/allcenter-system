package com.allcenter.moduleemployee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Si {@link #secret()} no está vacío, POST /api/auth/first-setup exige la cabecera {@code
 * X-First-Setup-Secret} con ese valor (recomendado en producción).
 */
@ConfigurationProperties(prefix = "app.first-setup")
public record FirstSetupProperties(String secret) {

    public FirstSetupProperties {
        if (secret == null) {
            secret = "";
        }
    }

    public boolean requiresSecret() {
        return !secret.isBlank();
    }
}
