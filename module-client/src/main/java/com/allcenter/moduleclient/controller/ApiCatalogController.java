package com.allcenter.moduleclient.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiCatalogController {

    @GetMapping("/api")
    public ResponseEntity<Map<String, Object>> catalog() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("module", "module-client");
        response.put(
                "publicEndpoints",
                java.util.List.of(
                        "POST /api/auth/login",
                        "POST /api/auth/register",
                        "POST /api/auth/refresh",
                        "POST /api/auth/logout"));
        response.put(
                "protectedEndpoints",
                java.util.List.of(
                        "GET /api/auth/me",
                        "POST /api/auth/change-password",
                        "POST /api/auth/logout-all",
                        "GET /api/clients",
                        "GET /api/clients/{id}",
                        "POST /api/clients",
                        "PUT /api/clients/{id}",
                        "DELETE /api/clients/{id}"));
        return ResponseEntity.ok(response);
    }
}
