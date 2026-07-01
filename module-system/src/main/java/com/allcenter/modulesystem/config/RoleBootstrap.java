package com.allcenter.modulesystem.config;

import com.allcenter.modulesystem.model.Role;
import com.allcenter.modulesystem.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@RequiredArgsConstructor
public class RoleBootstrap implements ApplicationRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(ApplicationArguments args) {
        ensureRole("USER", "Usuario estándar de la aplicación");
        ensureRole("ADMIN", "Administración completa del módulo");
        ensureRole("MASTER", "Superusuario de instalación (roles, empleados, auditoría)");
        ensureRole("HR", "Recursos humanos");
        ensureRole("PRODUCCION", "Operario de línea / escaneo OSI");
        ensureRole("ADMIN_PRODUCCION", "Coordinación y supervisión de producción");
        ensureRole("DESPACHO", "Operaciones de despacho y salida de pedidos");
        ensureRole("CHOFER", "Conductor / chofer de flota (RM, transporte)");
        ensureRole("SISTEMAS", "Control total del portal y configuración");
        ensureRole("ADMINISTRADOR", "Administración del portal (alias de ADMIN)");
        ensureRole("GERENCIA", "Gerencia operativa: editar, cancelar, imprimir");
        ensureRole("SEGURIDAD", "Recepción de mercadería y seguridad perimetral");
        ensureRole("PROCESOS", "Consulta y registro operativo de procesos");
        ensureRole("LOGISTICA", "Logística: guías, stock y palés (crear/leer)");
        ensureRole("CALIDAD", "Calidad: consulta y registro");
        ensureRole("VENTAS", "Ventas: proyecto optimización");
        ensureRole("ADMIN_VENTAS", "Admin ventas: proyectos, clientes portal y gestión comercial");
    }

    private void ensureRole(String name, String description) {
        if (roleRepository.existsByNameIgnoreCase(name)) {
            return;
        }
        Role role = new Role();
        role.setName(name.toUpperCase());
        role.setDescription(description);
        roleRepository.save(role);
    }
}
