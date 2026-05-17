package com.allcenter.security;

import java.util.Arrays;
import java.util.Set;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
public class ProductionSecretsValidator {

    private static final Set<String> WEAK_JWT_SECRETS =
            Set.of(
                    "change-this-to-a-long-random-secret-at-least-256-bits-for-hs256-algorithm",
                    "changeme",
                    "secret");

    private final Environment environment;
    private final AppSecurityProperties securityProperties;
    private final SharedJwtValidator jwtValidator;

    public ProductionSecretsValidator(
            Environment environment,
            AppSecurityProperties securityProperties,
            SharedJwtValidator jwtValidator) {
        this.environment = environment;
        this.securityProperties = securityProperties;
        this.jwtValidator = jwtValidator;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        if (!securityProperties.validateSecretsOnStartup()) {
            return;
        }
        boolean prodLike =
                Arrays.stream(environment.getActiveProfiles())
                        .anyMatch(p -> p.equals("prod") || p.equals("staging"));
        if (!prodLike) {
            return;
        }
        String jwtSecret = environment.getProperty("jwt.secret", "");
        if (!jwtValidator.isConfigured()
                || jwtSecret.isBlank()
                || WEAK_JWT_SECRETS.contains(jwtSecret.trim())) {
            throw new IllegalStateException(
                    "Perfil prod/staging: defina JWT_SECRET con un valor aleatorio de al menos 32 bytes");
        }
        String dbPassword = environment.getProperty("spring.datasource.password", "");
        if (dbPassword.isBlank()) {
            boolean biesse = "module-biesse".equals(environment.getProperty("spring.application.name"));
            String hint =
                    biesse
                            ? "BIESSE_DATASOURCE_PASSWORD"
                            : "SPRING_DATASOURCE_PASSWORD (o POSTGRES_PASSWORD en Docker)";
            throw new IllegalStateException(
                    "Perfil prod/staging: defina " + hint + " (sin contraseña por defecto en código)");
        }
    }
}
