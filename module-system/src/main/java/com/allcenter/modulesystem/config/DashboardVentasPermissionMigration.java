package com.allcenter.modulesystem.config;

import com.allcenter.modulesystem.model.Role;
import com.allcenter.modulesystem.model.RolePermission;
import com.allcenter.modulesystem.repository.RoleRepository;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Añade {@code view:dashboard.ventas} a roles existentes que deberían ver el panel de ventas
 * (sin reemplazar el resto de permisos).
 */
@Component
@Order(3)
@RequiredArgsConstructor
public class DashboardVentasPermissionMigration implements ApplicationRunner {

    private static final String ACTION_VIEW = "view";
    private static final String SUBJECT_RESUMEN = "dashboard.resumen";
    private static final String SUBJECT_VENTAS = "dashboard.ventas";

    private static final Set<String> ROLE_NAMES_WITH_VENTAS =
            Set.of("VENTAS", "ADMIN_VENTAS", "GERENCIA", "ADMIN", "ADMINISTRADOR", "SISTEMAS", "MASTER");

    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Role role : roleRepository.findAllWithPermissions()) {
            if (shouldGrantVentas(role) && !hasVentasPermission(role)) {
                RolePermission perm = new RolePermission();
                perm.setRole(role);
                perm.setAction(ACTION_VIEW);
                perm.setSubject(SUBJECT_VENTAS);
                role.getPermissions().add(perm);
                roleRepository.save(role);
            }
        }
    }

    private static boolean shouldGrantVentas(Role role) {
        String name = role.getName().trim().toUpperCase(Locale.ROOT);
        if (ROLE_NAMES_WITH_VENTAS.contains(name)) {
            return true;
        }
        if (role.getPermissions() == null || role.getPermissions().isEmpty()) {
            return false;
        }
        return role.getPermissions().stream()
                .anyMatch(
                        p ->
                                ACTION_VIEW.equalsIgnoreCase(p.getAction())
                                        && SUBJECT_RESUMEN.equalsIgnoreCase(p.getSubject()));
    }

    private static boolean hasVentasPermission(Role role) {
        if (role.getPermissions() == null) {
            return false;
        }
        return role.getPermissions().stream()
                .anyMatch(
                        p ->
                                ACTION_VIEW.equalsIgnoreCase(p.getAction())
                                        && SUBJECT_VENTAS.equalsIgnoreCase(p.getSubject()));
    }
}
