package com.allcenter.modulesystem.security;

import com.allcenter.modulesystem.service.EmployeePermissionService;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Reglas alineadas con `frontend/src/access/rolePermissions.js`:
 * <ul>
 *   <li>Sistemas / Master: control total (incl. eliminar)</li>
 *   <li>Administrador: crear, leer, editar, cancelar, imprimir (sin eliminar)</li>
 *   <li>Gerencia: igual operación que admin (sin menú gestión en UI)</li>
 *   <li>Roles operativos: solo crear y leer</li>
 *   <li>Permisos granulares en BD (action + subject) vía {@link EmployeePermissionService}</li>
 * </ul>
 */
@Component("portalAuth")
@RequiredArgsConstructor
public class PortalRoleAuthorization {

    private final EmployeePermissionService permissionService;

    public boolean canRead() {
        return isAuthenticated();
    }

    public boolean canCreate() {
        return isSystem() || isAdminOps() || isReadCreateOnly();
    }

    public boolean canUpdate() {
        return isSystem() || isAdminOps();
    }

    public boolean canCancel() {
        return isSystem() || isAdminOps();
    }

    /** Cerrar palé: finaliza el flujo de creación/escaneo (roles operativos incluidos). */
    public boolean canClose() {
        return canCreate();
    }

    public boolean canDelete() {
        return isSystem();
    }

    /** Eliminar proyectos de optimización desde Gestión (permiso {@code delete:gestion.proyectos}). */
    public boolean canDeleteGestionProyecto() {
        return isSystem() || permissionService.currentEmployeeHas("delete", "gestion.proyectos");
    }

    public boolean canAudit() {
        return isSystem() || isAdminOps();
    }

    public boolean canGestion() {
        return isGestion();
    }

    /** Admin ventas: gestión de clientes portal y proyectos de optimización. */
    public boolean canVentasGestion() {
        return hasAnyRole(Set.of(PortalRoleNames.ADMIN_VENTAS));
    }

    public boolean canGestionOrVentasGestion() {
        return canGestion() || canVentasGestion();
    }

    /** Solo rol MASTER (no SISTEMAS ni otros admin). */
    public boolean isMaster() {
        return hasAnyRole(Set.of(PortalRoleNames.MASTER));
    }

    public boolean canPrint() {
        return isSystem() || isAdminOps() || permissionService.currentEmployeeHasAction("print");
    }

    public boolean isSystem() {
        return hasAnyRole(PortalRoleNames.SYSTEM);
    }

    public boolean isGestion() {
        return hasAnyRole(PortalRoleNames.GESTION);
    }

    public boolean isAdminOps() {
        return hasAnyRole(PortalRoleNames.ADMIN_OPS);
    }

    public boolean isReadCreateOnly() {
        Set<String> roles = currentRoleNames();
        if (roles.isEmpty()) {
            return false;
        }
        if (isSystem() || isAdminOps()) {
            return false;
        }
        return roles.stream().anyMatch(PortalRoleNames.READ_CREATE::contains);
    }

    private static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
    }

    private static boolean hasAnyRole(Set<String> allowed) {
        Set<String> roles = currentRoleNames();
        return roles.stream().anyMatch(allowed::contains);
    }

    static Set<String> currentRoleNames() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Set.of();
        }
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        if (authorities == null) {
            return Set.of();
        }
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .map(PortalRoleAuthorization::normalizeRole)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    static String normalizeRole(String authority) {
        if (authority == null) {
            return "";
        }
        String s = authority.trim().toUpperCase();
        if (s.startsWith("ROLE_")) {
            s = s.substring(5);
        }
        return s.replace('-', '_');
    }
}
