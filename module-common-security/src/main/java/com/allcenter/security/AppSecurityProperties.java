package com.allcenter.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(
        /** Exige Bearer JWT válido en /api/** (microservicios sin Spring Security). */
        boolean apiAuthEnabled,
        /** HSTS, X-Frame-Options, etc. */
        boolean headersEnabled,
        /** Valida JWT_SECRET y credenciales al arrancar con perfil prod/staging. */
        boolean validateSecretsOnStartup) {

    public AppSecurityProperties {
        // defaults applied via application.properties in each module
    }
}
