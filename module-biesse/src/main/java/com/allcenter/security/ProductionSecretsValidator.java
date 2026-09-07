package com.allcenter.security;

import java.util.Arrays;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

/**
 * Nota: este archivo está duplicado tal cual en module-system y module-biesse
 * (paquete {@code com.allcenter.security} sin módulo Maven compartido). Cualquier fix
 * aquí debe replicarse manualmente en la otra copia hasta que se extraiga un módulo común.
 */
public class ProductionSecretsValidator {

    private static final Logger log = LoggerFactory.getLogger(ProductionSecretsValidator.class);

    private static final Set<String> WEAK_JWT_SECRETS =
            Set.of(
                    "change-this-to-a-long-random-secret-at-least-256-bits-for-hs256-algorithm",
                    "changeme",
                    "secret");

    private static final Set<String> KNOWN_DEV_PROFILES = Set.of("dev", "test", "local");

    private static final String DEFAULT_BIESSE_INTERNAL_TOKEN = "dev-biesse-internal";
    private static final String DEFAULT_MASTER_PASSWORD = "changeMeOnFirstDeploy";

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
        String[] activeProfiles = environment.getActiveProfiles();
        boolean knownDev = Arrays.stream(activeProfiles).anyMatch(KNOWN_DEV_PROFILES::contains);
        if (knownDev) {
            return;
        }
        if (activeProfiles.length == 0) {
            // Sin perfil activo: probablemente un arranque local (perfil "default" de Spring).
            // No se aborta el arranque para no romper el flujo local, pero se advierte fuerte
            // porque un despliegue real sin SPRING_PROFILES_ACTIVE=prod caería aquí también.
            log.warn(
                    "Arrancando sin ningún perfil Spring activo: NO se validan JWT_SECRET/contraseñas/"
                            + "tokens. Si esto es un despliegue real, defina SPRING_PROFILES_ACTIVE=prod "
                            + "(o staging); de lo contrario ignore este aviso.");
            return;
        }
        String jwtSecret = environment.getProperty("jwt.secret", "");
        if (!jwtValidator.isConfigured()
                || jwtSecret.isBlank()
                || WEAK_JWT_SECRETS.contains(jwtSecret.trim())) {
            throw new IllegalStateException(
                    "Defina JWT_SECRET con un valor aleatorio de al menos 32 bytes (perfil activo: "
                            + Arrays.toString(activeProfiles)
                            + ")");
        }
        String dbPassword = environment.getProperty("spring.datasource.password", "");
        if (dbPassword.isBlank()) {
            boolean biesse = "module-biesse".equals(environment.getProperty("spring.application.name"));
            String hint =
                    biesse
                            ? "BIESSE_DATASOURCE_PASSWORD"
                            : "SPRING_DATASOURCE_PASSWORD (o POSTGRES_PASSWORD en Docker)";
            throw new IllegalStateException("Defina " + hint + " (sin contraseña por defecto en código)");
        }
        String internalToken = environment.getProperty("app.biesse.internal-token", "").trim();
        if (internalToken.isBlank() || DEFAULT_BIESSE_INTERNAL_TOKEN.equals(internalToken)) {
            throw new IllegalStateException(
                    "Defina APP_BIESSE_INTERNAL_TOKEN con un valor aleatorio (no use el valor de desarrollo)");
        }
        boolean masterBootstrap =
                Boolean.parseBoolean(environment.getProperty("app.master-user.bootstrap", "false"));
        if (masterBootstrap) {
            String masterPassword = environment.getProperty("app.master-user.password", "").trim();
            if (masterPassword.isBlank() || DEFAULT_MASTER_PASSWORD.equals(masterPassword)) {
                throw new IllegalStateException(
                        "MASTER_USER_BOOTSTRAP=true requiere MASTER_USER_PASSWORD con un valor propio");
            }
        }
        boolean demoUserBootstrap =
                Boolean.parseBoolean(environment.getProperty("app.bootstrap-demo-user", "false"));
        if (demoUserBootstrap) {
            throw new IllegalStateException(
                    "app.bootstrap-demo-user=true no está permitido fuera de perfiles de desarrollo");
        }
    }
}
