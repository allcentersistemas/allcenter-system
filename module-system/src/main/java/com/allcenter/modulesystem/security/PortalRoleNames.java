package com.allcenter.modulesystem.security;

import java.util.Set;

/** Nombres de rol alineados con `frontend/src/auth/roles.js`. */
public final class PortalRoleNames {

    private PortalRoleNames() {}

    public static final String MASTER = "MASTER";
    public static final String SISTEMAS = "SISTEMAS";
    public static final String ADMIN = "ADMIN";
    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String GERENCIA = "GERENCIA";
    public static final String SEGURIDAD = "SEGURIDAD";
    public static final String PROCESOS = "PROCESOS";
    public static final String LOGISTICA = "LOGISTICA";
    public static final String CALIDAD = "CALIDAD";
    public static final String DESPACHO = "DESPACHO";
    public static final String PRODUCCION = "PRODUCCION";
    public static final String VENTAS = "VENTAS";
    public static final String ADMIN_VENTAS = "ADMIN_VENTAS";
    /** Compatibilidad */
    public static final String ADMIN_PRODUCCION = "ADMIN_PRODUCCION";
    public static final String USER = "USER";
    public static final String HR = "HR";
    public static final String CHOFER = "CHOFER";

    public static final Set<String> SYSTEM =
            Set.of(MASTER, SISTEMAS);

    public static final Set<String> GESTION =
            Set.of(MASTER, SISTEMAS, ADMIN, ADMINISTRADOR);

    public static final Set<String> ADMIN_OPS =
            Set.of(MASTER, SISTEMAS, ADMIN, ADMINISTRADOR, GERENCIA, ADMIN_PRODUCCION);

    public static final Set<String> READ_CREATE =
            Set.of(
                    SEGURIDAD,
                    PROCESOS,
                    LOGISTICA,
                    CALIDAD,
                    DESPACHO,
                    PRODUCCION,
                    VENTAS,
                    USER,
                    HR,
                    CHOFER);

    /**
     * Roles que reciben avisos de nuevas cotizaciones de optimización (además de permisos
     * granulares {@code view:project.list} / {@code view:gestion.proyectos}).
     */
    public static final Set<String> PROYECTO_QUOTE_NOTIFICATIONS =
            Set.of(
                    MASTER,
                    SISTEMAS,
                    ADMIN,
                    ADMINISTRADOR,
                    GERENCIA,
                    ADMIN_PRODUCCION,
                    VENTAS,
                    ADMIN_VENTAS);
}
