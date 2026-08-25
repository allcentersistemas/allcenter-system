package com.allcenter.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnWebApplication
@EnableConfigurationProperties({AppCorsProperties.class, AppSecurityProperties.class})
public class AppCorsAutoConfiguration implements WebMvcConfigurer {

    private final AppCorsProperties corsProperties;

    public AppCorsAutoConfiguration(AppCorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    SharedJwtValidator sharedJwtValidator(@Value("${jwt.secret:}") String jwtSecret) {
        return new SharedJwtValidator(jwtSecret);
    }

    @Bean
    BiessePortalRoleAuthorization biessePortalRoleAuthorization(SharedJwtValidator jwtValidator) {
        return new BiessePortalRoleAuthorization(jwtValidator);
    }

    @Bean
    ProductionSecretsValidator productionSecretsValidator(
            org.springframework.core.env.Environment environment,
            AppSecurityProperties securityProperties,
            SharedJwtValidator jwtValidator) {
        return new ProductionSecretsValidator(environment, securityProperties, jwtValidator);
    }

    @Bean
    FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter(
            AppSecurityProperties securityProperties) {
        FilterRegistrationBean<SecurityHeadersFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new SecurityHeadersFilter(securityProperties));
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        return reg;
    }

    @Bean
    FilterRegistrationBean<ApiJwtAuthFilter> apiJwtAuthFilter(
            AppSecurityProperties securityProperties, SharedJwtValidator jwtValidator) {
        FilterRegistrationBean<ApiJwtAuthFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new ApiJwtAuthFilter(securityProperties, jwtValidator));
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        reg.addUrlPatterns("/api/*");
        return reg;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders(
                        "Authorization",
                        "Content-Type",
                        "Accept",
                        "X-Requested-With",
                        "X-First-Setup-Secret",
                        "X-Actor-Employee-Id",
                        "X-Actor-Email",
                        "X-Agent-Token")
                .exposedHeaders("Authorization")
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Bean
    CorsConfigurationSource allcenterCorsConfigurationSource() {
        return AppCorsSupport.corsConfigurationSource(corsProperties.allowedOrigins());
    }
}
