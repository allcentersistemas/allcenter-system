package com.allcenter.security;

import java.util.List;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

public final class AppCorsSupport {

    private static final List<String> METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    private AppCorsSupport() {}

    public static CorsConfiguration buildConfiguration(List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(METHODS);
        config.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type",
                        "Accept",
                        "X-Requested-With",
                        "X-First-Setup-Secret",
                        "X-Actor-Employee-Id",
                        "X-Actor-Email",
                        "X-Agent-Token"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        return config;
    }

    public static CorsConfigurationSource corsConfigurationSource(List<String> allowedOrigins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", buildConfiguration(allowedOrigins));
        return source;
    }
}
