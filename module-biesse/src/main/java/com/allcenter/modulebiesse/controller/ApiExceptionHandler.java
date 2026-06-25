package com.allcenter.modulebiesse.controller;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        String message =
                ex.getReason() != null && !ex.getReason().isBlank()
                        ? ex.getReason()
                        : ex.getStatusCode().toString();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", message));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleDataAccess(DataAccessException ex) {
        String message = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();
        if (message != null && message.contains("no existe la relación")) {
            return ResponseEntity.status(503)
                    .body(
                            Map.of(
                                    "error", "database_schema_missing",
                                    "message",
                                    "La base de datos configurada no tiene tablas de escaneo (ordenes/partes/piezas). "
                                            + "Revise SPRING_DATASOURCE_URL."));
        }
        return ResponseEntity.status(500)
                .body(
                        Map.of(
                                "error", "database_error",
                                "message",
                                message != null && !message.isBlank()
                                        ? "Error de base de datos en module-biesse: " + message
                                        : "Error de base de datos en module-biesse."));
    }
}
