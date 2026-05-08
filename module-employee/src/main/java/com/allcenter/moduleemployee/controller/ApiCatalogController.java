package com.allcenter.moduleemployee.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Índice de la API para Postman u otros clientes (sin autenticación).
 */
@RestController
public class ApiCatalogController {

    @GetMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> catalog() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("service", "module-employee");
        root.put(
                "description",
                "Catálogo de rutas. Access JWT en Authorization: Bearer <access>; renovar con POST /api/auth/refresh (body refreshToken) cuando caduque.");
        root.put("endpoints", endpoints());
        return root;
    }

    private static List<Map<String, String>> endpoints() {
        return List.of(
                ep("GET", "/api", "Este catálogo", "público"),
                ep("GET", "/api/auth/first-setup/status", "true si aún no hay empleados (puede usar first-setup)", "público"),
                ep(
                        "POST",
                        "/api/auth/first-setup",
                        "Crear primer usuario MASTER (solo base vacía); devuelve tokens; opcional X-First-Setup-Secret",
                        "público"),
                ep("POST", "/api/auth/login", "Login → accessToken + refreshToken + employee", "público"),
                ep(
                        "POST",
                        "/api/auth/register",
                        "Registro (email, password, datos personales, roleIds[] permitidos por app.registration.allowed-role-names)",
                        "público"),
                ep("POST", "/api/auth/refresh", "Nuevo access + refresh (rotación del refresh anterior)", "público"),
                ep("POST", "/api/auth/logout", "Revoca el refresh token enviado (body refreshToken)", "público"),
                ep("POST", "/api/auth/logout-all", "Revoca todos los refresh del usuario autenticado", "Bearer"),
                ep("GET", "/api/auth/me", "Perfil del usuario autenticado", "Bearer"),
                ep("GET", "/api/employees/me", "Mismo perfil que /api/auth/me", "Bearer"),
                ep("PATCH", "/api/employees/me", "Actualizar datos personales (campos limitados)", "Bearer"),
                ep("GET", "/api/employees", "Listar empleados", "Bearer + MASTER o ADMIN"),
                ep("POST", "/api/employees", "Crear empleado", "Bearer + MASTER o ADMIN"),
                ep("GET", "/api/employees/{id}", "Detalle (propio o MASTER/ADMIN)", "Bearer"),
                ep("PATCH", "/api/employees/{id}", "Actualizar empleado", "Bearer + MASTER o ADMIN"),
                ep("PUT", "/api/employees/{id}/roles", "Reemplazar roles (body roleIds[])", "Bearer + MASTER o ADMIN"),
                ep("DELETE", "/api/employees/{id}", "Baja lógica (active=false)", "Bearer + MASTER o ADMIN"),
                ep("GET", "/api/roles", "Listar roles", "Bearer"),
                ep("GET", "/api/roles/{id}", "Detalle de rol", "Bearer"),
                ep("POST", "/api/roles", "Crear rol", "Bearer + MASTER o ADMIN"),
                ep("PATCH", "/api/roles/{id}", "Actualizar rol", "Bearer + MASTER o ADMIN"),
                ep("DELETE", "/api/roles/{id}", "Eliminar rol (si no está asignado)", "Bearer + MASTER o ADMIN"),
                ep("GET", "/api/audit/entries", "Listado paginado de auditoría", "Bearer + MASTER o ADMIN"),
                ep("GET", "/api/audit/entries/{id}", "Detalle de evento de auditoría", "Bearer + MASTER o ADMIN"));
    }

    private static Map<String, String> ep(String method, String path, String description, String security) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("method", method);
        m.put("path", path);
        m.put("description", description);
        m.put("security", security);
        return m;
    }
}
