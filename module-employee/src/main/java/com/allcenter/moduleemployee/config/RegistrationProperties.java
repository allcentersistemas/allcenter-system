package com.allcenter.moduleemployee.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.registration")
public record RegistrationProperties(String allowedRoleNames) {

    public RegistrationProperties {
        if (allowedRoleNames == null || allowedRoleNames.isBlank()) {
            allowedRoleNames = "USER";
        }
    }

    /** Nombres de rol (mayúsculas) que un usuario puede asignarse en POST /api/auth/register. */
    public Set<String> allowedRoleNamesSet() {
        return Arrays.stream(allowedRoleNames.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
