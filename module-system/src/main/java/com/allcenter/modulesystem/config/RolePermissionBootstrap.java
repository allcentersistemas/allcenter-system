package com.allcenter.modulesystem.config;

import com.allcenter.modulesystem.dto.PermissionRuleDto;
import com.allcenter.modulesystem.model.Role;
import com.allcenter.modulesystem.model.RolePermission;
import com.allcenter.modulesystem.repository.RoleRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Semilla de permisos CASL por nombre de rol (equivalente a {@code rolePermissions.js}).
 * Solo rellena roles que aún no tienen permisos en BD.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class RolePermissionBootstrap implements ApplicationRunner {

    private final RoleRepository roleRepository;

    private static final String ACTION_VIEW = "view";
    private static final String ACTION_CREATE = "create";
    private static final String ACTION_UPDATE = "update";
    private static final String ACTION_DELETE = "delete";
    private static final String ACTION_CANCEL = "cancel";
    private static final String ACTION_SCAN = "scan";
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_PRINT = "print";
    private static final String ACTION_AUDIT = "audit";
    private static final String ACTION_MANAGE = "manage";

    private static final List<String> READ_CREATE =
            List.of(ACTION_VIEW, ACTION_CREATE, ACTION_CLOSE);
    private static final List<String> ADMIN_OPS =
            List.of(ACTION_VIEW, ACTION_CREATE, ACTION_UPDATE, ACTION_CANCEL, ACTION_PRINT);
    private static final List<String> AUDIT_VIEW = List.of(ACTION_VIEW, ACTION_AUDIT);
    private static final List<String> ALL_ACTIONS =
            List.of(
                    ACTION_VIEW,
                    ACTION_CREATE,
                    ACTION_UPDATE,
                    ACTION_DELETE,
                    ACTION_CANCEL,
                    ACTION_SCAN,
                    ACTION_CLOSE,
                    ACTION_PRINT,
                    ACTION_AUDIT);

    private static final String F_BIESSE_ORDERS = "biesse.orders";
    private static final String F_BIESSE_SCAN = "biesse.scan";
    private static final String F_BIESSE_STICKERS = "biesse.stickers";
    private static final String F_BIESSE_TOOLS = "biesse.tools";
    private static final String F_PALES_LIST = "pales.list";
    private static final String F_PALES_OPS = "pales.operaciones";
    private static final String F_PALES_PRINT = "pales.print";
    private static final String F_INV_GUIAS = "inventory.guias";
    private static final String F_INV_STOCK = "inventory.stock";
    private static final String F_INV_TABLEROS = "inventory.tableros";
    private static final String F_INV_CANTOS = "inventory.cantos";
    private static final String F_INV_RM = "inventory.rm";
    private static final String F_TRANSPORT_LOADS = "transport.loads";
    private static final String F_TRANSPORT_VEHICLES = "transport.vehicles";
    private static final String F_PROJECT_LIST = "project.list";
    private static final String F_BIESSE_AUDIT = "biesse.audit";
    private static final String F_PALES_AUDIT = "pales.audit";
    private static final String F_TRANSPORT_AUDIT = "transport.audit";
    private static final String F_STICKER_AUDIT = "biesse.stickerAudit";
    private static final String F_EMPLOYEE_ADMIN = "employee.admin";
    private static final String F_LOCATION = "location.catalog";
    private static final String F_API = "api.catalog";
    private static final String F_DASHBOARD = "dashboard.resumen";
    private static final String F_DASHBOARD_VENTAS = "dashboard.ventas";
    private static final String F_GESTION_CLIENTES = "gestion.clientes";
    private static final String F_GESTION_PROYECTOS = "gestion.proyectos";

  private static final String[] OPS_FEATURES = {
        F_BIESSE_ORDERS,
        F_BIESSE_SCAN,
        F_BIESSE_STICKERS,
        F_BIESSE_TOOLS,
        F_PALES_LIST,
        F_PALES_OPS,
        F_PALES_PRINT,
        F_INV_GUIAS,
        F_INV_STOCK,
        F_INV_TABLEROS,
        F_INV_CANTOS,
        F_INV_RM,
        F_TRANSPORT_LOADS,
        F_TRANSPORT_VEHICLES,
        F_PROJECT_LIST
    };

    private static final String[] AUDIT_FEATURES = {
        F_BIESSE_AUDIT, F_PALES_AUDIT, F_TRANSPORT_AUDIT, F_STICKER_AUDIT
    };

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, List<PermissionRuleDto>> catalog = buildCatalog();
        for (Role role : roleRepository.findAllWithPermissions()) {
            if (role.getPermissions() != null && !role.getPermissions().isEmpty()) {
                continue;
            }
            List<PermissionRuleDto> defaults = catalog.get(role.getName().toUpperCase(Locale.ROOT));
            if (defaults == null || defaults.isEmpty()) {
                continue;
            }
            replacePermissions(role, defaults);
            roleRepository.save(role);
        }
    }

    private static Map<String, List<PermissionRuleDto>> buildCatalog() {
        List<PermissionRuleDto> readCreateOps = rules(READ_CREATE, OPS_FEATURES);
        List<PermissionRuleDto> adminOps = rules(ADMIN_OPS, OPS_FEATURES);
        List<PermissionRuleDto> auditRules = rules(AUDIT_VIEW, AUDIT_FEATURES);
        List<PermissionRuleDto> gestionAdmin = new ArrayList<>();
        gestionAdmin.addAll(rules(ALL_ACTIONS, F_EMPLOYEE_ADMIN));
        gestionAdmin.addAll(rules(ALL_ACTIONS, F_LOCATION));
        gestionAdmin.add(new PermissionRuleDto(ACTION_VIEW, F_API));
        gestionAdmin.addAll(rules(ALL_ACTIONS, F_TRANSPORT_VEHICLES));

        List<PermissionRuleDto> master =
                List.of(
                        new PermissionRuleDto(ACTION_MANAGE, "all"),
                        new PermissionRuleDto(ACTION_VIEW, F_DASHBOARD),
                        new PermissionRuleDto(ACTION_VIEW, F_DASHBOARD_VENTAS));

        List<PermissionRuleDto> sistemas = new ArrayList<>();
        sistemas.add(new PermissionRuleDto(ACTION_MANAGE, "all"));
        sistemas.add(new PermissionRuleDto(ACTION_VIEW, F_DASHBOARD));
        sistemas.add(new PermissionRuleDto(ACTION_VIEW, F_DASHBOARD_VENTAS));
        sistemas.addAll(gestionAdmin);
        sistemas.addAll(auditRules);
        sistemas.addAll(rules(ALL_ACTIONS, OPS_FEATURES));

        List<PermissionRuleDto> admin = new ArrayList<>();
        admin.add(new PermissionRuleDto(ACTION_VIEW, F_DASHBOARD));
        admin.add(new PermissionRuleDto(ACTION_VIEW, F_DASHBOARD_VENTAS));
        admin.addAll(gestionAdmin);
        admin.addAll(auditRules);
        admin.addAll(adminOps);

        List<PermissionRuleDto> gerencia = new ArrayList<>(adminOps);
        gerencia.add(new PermissionRuleDto(ACTION_VIEW, F_DASHBOARD_VENTAS));
        gerencia.addAll(auditRules);

        List<PermissionRuleDto> ventas = new ArrayList<>(rules(READ_CREATE, F_PROJECT_LIST));
        ventas.add(new PermissionRuleDto(ACTION_VIEW, F_DASHBOARD_VENTAS));

        List<PermissionRuleDto> adminVentas = new ArrayList<>(ventas);
        adminVentas.addAll(
                rules(
                        List.of(ACTION_VIEW, ACTION_CREATE, ACTION_UPDATE, ACTION_CANCEL),
                        F_GESTION_CLIENTES,
                        F_GESTION_PROYECTOS));

        return Map.ofEntries(
                Map.entry("MASTER", master),
                Map.entry("SISTEMAS", sistemas),
                Map.entry("ADMIN", admin),
                Map.entry("ADMINISTRADOR", admin),
                Map.entry("GERENCIA", gerencia),
                Map.entry("SEGURIDAD", readCreateOps),
                Map.entry("PROCESOS", readCreateOps),
                Map.entry("LOGISTICA", readCreateOps),
                Map.entry("CALIDAD", readCreateOps),
                Map.entry("DESPACHO", readCreateOps),
                Map.entry("PRODUCCION", readCreateOps),
                Map.entry("VENTAS", ventas),
                Map.entry("ADMIN_VENTAS", adminVentas),
                Map.entry("ADMIN_PRODUCCION", gerencia));
    }

    private static List<PermissionRuleDto> rules(List<String> actions, String... subjects) {
        List<PermissionRuleDto> out = new ArrayList<>();
        for (String subject : subjects) {
            for (String action : actions) {
                out.add(new PermissionRuleDto(action, subject));
            }
        }
        return out;
    }

    private static void replacePermissions(Role role, List<PermissionRuleDto> rules) {
        role.getPermissions().clear();
        Set<String> seen = new LinkedHashSet<>();
        for (PermissionRuleDto rule : rules) {
            String key = rule.action() + ":" + rule.subject();
            if (!seen.add(key)) {
                continue;
            }
            RolePermission perm = new RolePermission();
            perm.setRole(role);
            perm.setAction(rule.action().trim());
            perm.setSubject(rule.subject().trim());
            role.getPermissions().add(perm);
        }
    }
}
