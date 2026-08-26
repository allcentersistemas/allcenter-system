package com.allcenter.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiJwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final AppSecurityProperties securityProperties;
    private final SharedJwtValidator jwtValidator;

    public ApiJwtAuthFilter(AppSecurityProperties securityProperties, SharedJwtValidator jwtValidator) {
        this.securityProperties = securityProperties;
        this.jwtValidator = jwtValidator;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!securityProperties.apiAuthEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        if (PATH_MATCHER.match("/actuator/**", path)
                || PATH_MATCHER.match("/error", path)
                || PATH_MATCHER.match("/api/biesse/scan/integration/**", path)
                || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !PATH_MATCHER.match("/api/**", path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, "MISSING_TOKEN", "Authorization Bearer token is required");
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            if (!jwtValidator.isValidAccessToken(token)) {
                writeUnauthorized(response, "ACCESS_TOKEN_INVALID", "Invalid access token");
                return;
            }
        } catch (ExpiredJwtException e) {
            writeUnauthorized(response, "ACCESS_TOKEN_EXPIRED", "Access token expired");
            return;
        } catch (JwtException | IllegalStateException e) {
            writeUnauthorized(response, "ACCESS_TOKEN_INVALID", "Invalid access token");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String code, String message)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body =
                "{\"code\":\""
                        + code
                        + "\",\"message\":\""
                        + message.replace("\"", "\\\"")
                        + "\"}";
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}
