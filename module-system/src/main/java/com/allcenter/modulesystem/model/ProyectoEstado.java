package com.allcenter.modulesystem.model;

public enum ProyectoEstado {
    ENVIADO,
    EN_ATENCION,
    COTIZADO,
    VENDIDO,
    OPTIMIZADO,
    PRODUCCION,
    DESPACHO,
    LISTO_PARA_ENTREGAR,
    ENTREGADO,
    CANCELADO;

    public static ProyectoEstado fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        if ("ENVIANDO".equals(normalized)) {
            return ENVIADO;
        }
        if ("LISTO".equals(normalized) || "LISTO_ENTREGAR".equals(normalized)) {
            return LISTO_PARA_ENTREGAR;
        }
        return ProyectoEstado.valueOf(normalized);
    }

    public boolean isTerminal() {
        return this == ENTREGADO || this == CANCELADO;
    }

    /** Ya vendido o más adelante en el flujo operativo. */
    public boolean isPostVenta() {
        return this == VENDIDO
                || this == OPTIMIZADO
                || this == PRODUCCION
                || this == DESPACHO
                || this == LISTO_PARA_ENTREGAR
                || this == ENTREGADO;
    }

    public boolean canAdvanceTo(ProyectoEstado next) {
        if (next == null || this == next) {
            return false;
        }
        if (this == CANCELADO || this == ENTREGADO) {
            return false;
        }
        return ordinal() < next.ordinal();
    }
}
