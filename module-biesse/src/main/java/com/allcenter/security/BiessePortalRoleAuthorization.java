package com.allcenter.security;

import io.jsonwebtoken.Claims;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Misma matriz que module-system `PortalRoleAuthorization`. */
public class BiessePortalRoleAuthorization {

    private static final Set<String> SYSTEM = Set.of("MASTER", "SISTEMAS");
    private static final Set<String> ADMIN_OPS =
            Set.of("MASTER", "SISTEMAS", "ADMIN", "ADMINISTRADOR", "GERENCIA", "ADMIN_PRODUCCION");
    private static final Set<String> READ_CREATE =
            Set.of(
                    "SEGURIDAD",
                    "PROCESOS",
                    "LOGISTICA",
                    "CALIDAD",
                    "DESPACHO",
                    "PRODUCCION",
                    "VENTAS",
                    "USER",
                    "HR",
                    "CHOFER");

    private final SharedJwtValidator jwtValidator;

    public BiessePortalRoleAuthorization(SharedJwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    public void requireRead(String authorization) {
        requireAuthenticated(authorization);
    }

    public void requireCreate(String authorization) {
        if (!canCreate(authorization)) {
            deny();
        }
    }

    public void requireAdminOps(String authorization) {
        if (!canAdminOps(authorization)) {
            deny();
        }
    }

    public void requireAudit(String authorization) {
        if (!canAudit(authorization)) {
            deny();
        }
    }

    public boolean canCreate(String authorization) {
        Set<String> roles = rolesFromAuthorization(authorization);
        return hasAny(roles, SYSTEM) || hasAny(roles, ADMIN_OPS) || hasAny(roles, READ_CREATE);
    }

    public boolean canAdminOps(String authorization) {
        Set<String> roles = rolesFromAuthorization(authorization);
        return hasAny(roles, SYSTEM) || hasAny(roles, ADMIN_OPS);
    }

    public boolean canAudit(String authorization) {
        return canAdminOps(authorization);
    }

    private void requireAuthenticated(String authorization) {
        rolesFromAuthorization(authorization);
    }

    private Set<String> rolesFromAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            deny();
        }
        String token = authorization.substring(7).trim();
        try {
            Claims claims = jwtValidator.parseClaims(token);
            Object raw = claims.get("roles");
            if (raw instanceof Collection<?> col) {
                return col.stream()
                        .map(String::valueOf)
                        .map(BiessePortalRoleAuthorization::normalizeRole)
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.toSet());
            }
            if (raw instanceof List<?> list) {
                return list.stream()
                        .map(String::valueOf)
                        .map(BiessePortalRoleAuthorization::normalizeRole)
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.toSet());
            }
            return Set.of();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido");
        }
    }

    private static boolean hasAny(Set<String> userRoles, Set<String> allowed) {
        return userRoles.stream().anyMatch(allowed::contains);
    }

    private static String normalizeRole(String authority) {
        String s = authority.trim().toUpperCase();
        if (s.startsWith("ROLE_")) {
            s = s.substring(5);
        }
        return s.replace('-', '_');
    }

    private static void deny() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permiso para esta operación");
    }
}
