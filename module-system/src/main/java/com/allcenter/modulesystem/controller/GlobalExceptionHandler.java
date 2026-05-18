package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.exception.ApiException;
import com.allcenter.modulesystem.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        logApiError(ex.getStatus(), ex, request, false);
        return ResponseEntity.status(ex.getStatus())
                .body(
                        ApiErrorResponse.build(
                                request, ex.getStatus(), ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUpload(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        logApiError(HttpStatus.PAYLOAD_TOO_LARGE, ex, request, false);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(
                        ApiErrorResponse.build(
                                request,
                                HttpStatus.PAYLOAD_TOO_LARGE,
                                "PAYLOAD_TOO_LARGE",
                                "El envío supera el tamaño máximo permitido (demasiadas fotos o muy pesadas). "
                                        + "Use menos fotos o vuelva a tomarlas con menor resolución."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(fe -> details.put(fe.getField(), fe.getDefaultMessage()));
        ex.getBindingResult()
                .getGlobalErrors()
                .forEach(
                        ge ->
                                details.put(
                                        ge.getObjectName(),
                                        ge.getDefaultMessage() != null
                                                ? ge.getDefaultMessage()
                                                : "Invalid"));
        String message =
                details.isEmpty()
                        ? "La validación del cuerpo de la petición ha fallado"
                        : "Hay campos inválidos; revise el objeto \"details\"";
        logApiError(HttpStatus.BAD_REQUEST, ex, request, false);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiErrorResponse.build(
                                request,
                                HttpStatus.BAD_REQUEST,
                                "VALIDATION_ERROR",
                                message,
                                details));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        log.warn(
                "[API] {} {} -> 401 INVALID_CREDENTIALS (login fallido)",
                request.getMethod(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(
                        ApiErrorResponse.build(
                                request,
                                HttpStatus.UNAUTHORIZED,
                                "INVALID_CREDENTIALS",
                                "Usuario o contraseña incorrectos"));
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotFound(
            UsernameNotFoundException ex, HttpServletRequest request) {
        logApiError(HttpStatus.UNAUTHORIZED, ex, request, false);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(
                        ApiErrorResponse.build(
                                request,
                                HttpStatus.UNAUTHORIZED,
                                "USER_NOT_FOUND",
                                ex.getMessage() != null ? ex.getMessage() : "Usuario no encontrado"));
    }

    @ExceptionHandler({DisabledException.class, LockedException.class})
    public ResponseEntity<ApiErrorResponse> handleAccountDisabled(
            Exception ex, HttpServletRequest request) {
        logApiError(HttpStatus.FORBIDDEN, ex, request, false);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(
                        ApiErrorResponse.build(
                                request,
                                HttpStatus.FORBIDDEN,
                                "ACCOUNT_DISABLED",
                                ex.getMessage() != null ? ex.getMessage() : "Cuenta no disponible"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        logApiError(HttpStatus.UNAUTHORIZED, ex, request, false);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(
                        ApiErrorResponse.build(
                                request,
                                HttpStatus.UNAUTHORIZED,
                                "AUTHENTICATION_FAILED",
                                ex.getMessage() != null ? ex.getMessage() : "Autenticación fallida"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = "No tiene permiso para acceder a " + request.getRequestURI();
        }
        logApiError(HttpStatus.FORBIDDEN, ex, request, false);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.build(request, HttpStatus.FORBIDDEN, "ACCESS_DENIED", msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        Throwable cause = ex.getMostSpecificCause();
        String detail =
                cause != null && cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
        logApiError(HttpStatus.BAD_REQUEST, ex, request, false);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiErrorResponse.build(
                                request,
                                HttpStatus.BAD_REQUEST,
                                "INVALID_REQUEST_BODY",
                                detail != null
                                        ? detail
                                        : "El cuerpo JSON no es válido o no coincide con el tipo esperado"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        logApiError(HttpStatus.BAD_REQUEST, ex, request, false);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiErrorResponse.build(
                                request,
                                HttpStatus.BAD_REQUEST,
                                "MISSING_PARAMETER",
                                "Falta el parámetro requerido \"" + ex.getParameterName() + "\" de tipo "
                                        + ex.getParameterType()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String name = ex.getName();
        String required =
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "desconocido";
        Object value = ex.getValue();
        logApiError(HttpStatus.BAD_REQUEST, ex, request, false);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiErrorResponse.build(
                                request,
                                HttpStatus.BAD_REQUEST,
                                "TYPE_MISMATCH",
                                "El parámetro \"" + name + "\" con valor \"" + value
                                        + "\" no se puede convertir a " + required));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        Throwable cause = ex.getMostSpecificCause();
        String msg = cause != null ? cause.getMessage() : ex.getMessage();
        logApiError(HttpStatus.CONFLICT, ex, request, false);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        ApiErrorResponse.build(
                                request,
                                HttpStatus.CONFLICT,
                                "DATA_INTEGRITY_VIOLATION",
                                msg != null ? msg : "Violación de integridad de datos en la base de datos"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {
        logApiError(HttpStatus.INTERNAL_SERVER_ERROR, ex, request, true);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ApiErrorResponse.build(
                                request,
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "INTERNAL_STATE_ERROR",
                                ex.getMessage() != null ? ex.getMessage() : "Estado interno inválido"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        logApiError(HttpStatus.BAD_REQUEST, ex, request, false);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiErrorResponse.build(
                                request,
                                HttpStatus.BAD_REQUEST,
                                "INVALID_ARGUMENT",
                                ex.getMessage() != null ? ex.getMessage() : "Argumento inválido"));
    }

    @ExceptionHandler(StackOverflowError.class)
    public ResponseEntity<ApiErrorResponse> handleStackOverflow(
            StackOverflowError ex, HttpServletRequest request) {
        log.error("[API] StackOverflowError en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ApiErrorResponse.build(
                                request,
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "STACK_OVERFLOW",
                                "Error interno al procesar la petición; contacte al administrador"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAny(Exception ex, HttpServletRequest request) {
        logApiError(HttpStatus.INTERNAL_SERVER_ERROR, ex, request, true);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ApiErrorResponse.build(
                                request,
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "UNEXPECTED_ERROR",
                                ex.getMessage() != null ? ex.getMessage() : "Error inesperado"));
    }

    /**
     * Registra en consola el mismo contexto que verás en Postman (path, método, código). Los 5xx
     * incluyen stack trace completo.
     */
    private static void logApiError(
            HttpStatus status, Exception ex, HttpServletRequest request, boolean forceStack) {
        String line =
                request != null
                        ? request.getMethod() + " " + request.getRequestURI()
                        : "request-desconocido";
        if (status.is5xxServerError() || forceStack) {
            log.error(
                    "[API] {} -> {} {}: {}",
                    line,
                    status.value(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage(),
                    ex);
        } else {
            log.warn(
                    "[API] {} -> {} {}: {}",
                    line,
                    status.value(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
        }
    }
}
