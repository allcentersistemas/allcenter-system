package com.allcenter.modulesystem.config;

import com.allcenter.modulesystem.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

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
                        : "Token invalido o ausente";
        ApiErrorResponse body =
                ApiErrorResponse.build(request, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", reason);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
