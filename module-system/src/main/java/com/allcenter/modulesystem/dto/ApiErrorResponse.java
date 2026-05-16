package com.allcenter.modulesystem.dto;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;

public record ApiErrorResponse(
        boolean success,
        String code,
        String message,
        int status,
        String path,
        OffsetDateTime timestamp,
        Map<String, String> details) {

    public static ApiErrorResponse build(HttpServletRequest request, HttpStatus status, String code, String message) {
        return new ApiErrorResponse(
                false,
                code,
                message,
                status.value(),
                request.getRequestURI(),
                OffsetDateTime.now(),
                null);
    }

    public static ApiErrorResponse build(
            HttpServletRequest request,
            HttpStatus status,
            String code,
            String message,
            Map<String, String> details) {
        return new ApiErrorResponse(
                false,
                code,
                message,
                status.value(),
                request.getRequestURI(),
                OffsetDateTime.now(),
                details);
    }
}
