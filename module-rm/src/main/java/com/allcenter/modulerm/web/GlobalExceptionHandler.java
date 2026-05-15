package com.allcenter.modulerm.web;

import com.allcenter.modulerm.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.build(
                        request,
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "Hay campos invalidos en la solicitud",
                        details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.build(
                        request,
                        HttpStatus.BAD_REQUEST,
                        "INVALID_REQUEST_BODY",
                        "El cuerpo JSON no es valido"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String code =
                switch (status) {
                    case NOT_FOUND -> "NOT_FOUND";
                    case CONFLICT -> "CONFLICT";
                    case BAD_REQUEST -> "BAD_REQUEST";
                    default -> "BUSINESS_ERROR";
                };
        String message = ex.getReason() == null ? "Error de negocio" : ex.getReason();
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.build(request, status, code, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.build(
                        request,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "UNEXPECTED_ERROR",
                        "Ocurrio un error inesperado"));
    }
}
