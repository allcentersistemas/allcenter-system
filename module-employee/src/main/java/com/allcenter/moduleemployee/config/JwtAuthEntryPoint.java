package com.allcenter.moduleemployee.config;

import com.allcenter.moduleemployee.model.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String reason =
                authException.getMessage() != null && !authException.getMessage().isBlank()
                        ? authException.getMessage()
                        : "Token inválido o ausente; envíe cabecera Authorization: Bearer <token>";
        log.warn(
                "[API] {} {} -> 401 UNAUTHORIZED (filtro seguridad): {}",
                request.getMethod(),
                request.getRequestURI(),
                reason);
        ApiErrorResponse body =
                ApiErrorResponse.build(request, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", reason);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
