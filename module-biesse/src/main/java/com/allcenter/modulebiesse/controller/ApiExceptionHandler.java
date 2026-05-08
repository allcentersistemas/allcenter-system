package com.allcenter.modulebiesse.controller;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

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
                                "message", "Error de base de datos en module-biesse."));
    }
}
