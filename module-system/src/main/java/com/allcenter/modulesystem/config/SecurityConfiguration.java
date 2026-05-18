package com.allcenter.modulesystem.config;

import com.allcenter.modulesystem.security.ClientJwtAuthenticationFilter;
import com.allcenter.modulesystem.security.ClientUserDetailsService;
import com.allcenter.modulesystem.security.EmployeeJwtAuthenticationFilter;
import com.allcenter.modulesystem.security.EmployeeUserDetailsService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    private final EmployeeUserDetailsService employeeUserDetailsService;
    private final ClientUserDetailsService clientUserDetailsService;
    private final EmployeeJwtAuthenticationFilter employeeJwtAuthenticationFilter;
    private final ClientJwtAuthenticationFilter clientJwtAuthenticationFilter;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final CorsConfigurationSource allcenterCorsConfigurationSource;
    private final AuthEndpointProperties authEndpointProperties;

    public SecurityConfiguration(
            EmployeeUserDetailsService employeeUserDetailsService,
            ClientUserDetailsService clientUserDetailsService,
            EmployeeJwtAuthenticationFilter employeeJwtAuthenticationFilter,
            ClientJwtAuthenticationFilter clientJwtAuthenticationFilter,
            JwtAuthEntryPoint jwtAuthEntryPoint,
            CorsConfigurationSource allcenterCorsConfigurationSource,
            AuthEndpointProperties authEndpointProperties) {
        this.employeeUserDetailsService = employeeUserDetailsService;
        this.clientUserDetailsService = clientUserDetailsService;
        this.employeeJwtAuthenticationFilter = employeeJwtAuthenticationFilter;
        this.clientJwtAuthenticationFilter = clientJwtAuthenticationFilter;
        this.jwtAuthEntryPoint = jwtAuthEntryPoint;
        this.allcenterCorsConfigurationSource = allcenterCorsConfigurationSource;
        this.authEndpointProperties = authEndpointProperties;
    }

    @Bean
    @Order(1)
    SecurityFilterChain clientSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("clientAuthenticationManager") AuthenticationManager clientAuthenticationManager)
            throws Exception {
        http.securityMatcher("/api/clients/**", "/api/client/**")
                .authenticationManager(clientAuthenticationManager)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(allcenterCorsConfigurationSource))
                .headers(this::applySecurityHeaders)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(jwtAuthEntryPoint))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                HttpMethod.POST,
                                                "/api/client/auth/login",
                                                "/api/client/auth/refresh",
                                                "/api/client/auth/logout")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/api/client/auth/register")
                                        .access(
                                                (a, ctx) ->
                                                        new AuthorizationDecision(
                                                                authEndpointProperties.registrationEnabled()))
                                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/actuator/info")
                                        .permitAll()
                                        .requestMatchers("/error")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .authenticationProvider(clientAuthenticationProvider())
                .addFilterBefore(clientJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain employeeSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("employeeAuthenticationManager") AuthenticationManager employeeAuthenticationManager)
            throws Exception {
        http.authenticationManager(employeeAuthenticationManager)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(allcenterCorsConfigurationSource))
                .headers(this::applySecurityHeaders)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(jwtAuthEntryPoint))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                HttpMethod.POST,
                                                "/api/auth/login",
                                                "/api/auth/refresh",
                                                "/api/auth/logout")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/api/auth/register")
                                        .access(
                                                (a, ctx) ->
                                                        new AuthorizationDecision(
                                                                authEndpointProperties.registrationEnabled()))
                                        // Sin JWT; EmployeeAuthService valida flag, BD vacía y secret opcional
                                        .requestMatchers(HttpMethod.POST, "/api/auth/first-setup")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/auth/first-setup/status")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/actuator/info")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api")
                                        .permitAll()
                                        .requestMatchers("/error")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .authenticationProvider(employeeAuthenticationProvider())
                .addFilterBefore(employeeJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void applySecurityHeaders(
            org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<?> h) {
        h.contentTypeOptions(Customizer.withDefaults())
                .frameOptions(f -> f.deny())
                .referrerPolicy(
                        r ->
                                r.policy(
                                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                                .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .httpStrictTransportSecurity(s -> s.includeSubDomains(true).maxAgeInSeconds(31536000));
    }

    private AuthenticationProvider employeeAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(employeeUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    private AuthenticationProvider clientAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(clientUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
