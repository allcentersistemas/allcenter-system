package com.allcenter.moduleclient.model.dto;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

public record ApiErrorResponse(
        String timestamp,
        int status,
        String path,
        String method,
        String code,
        String message,
        Map<String, String> details) {

    public static ApiErrorResponse build(
            HttpServletRequest request,
            HttpStatus httpStatus,
            String code,
            String message,
            Map<String, String> details) {
        return new ApiErrorResponse(
                Instant.now().toString(),
                httpStatus.value(),
                request != null ? request.getRequestURI() : null,
                request != null ? request.getMethod() : null,
                code,
                message,
                details);
    }

    public static ApiErrorResponse build(
            HttpServletRequest request, HttpStatus httpStatus, String code, String message) {
        return build(request, httpStatus, code, message, null);
    }
}
