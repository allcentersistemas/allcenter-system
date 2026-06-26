package com.allcenter.modulesystem.model;

public enum ProyectoEstado {
    ENVIADO,
    EN_ATENCION,
    COTIZADO,
    VENDIDO,
    CANCELADO;

    public static ProyectoEstado fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        if ("ENVIANDO".equals(normalized)) {
            return ENVIADO;
        }
        return ProyectoEstado.valueOf(normalized);
    }

    public boolean isTerminal() {
        return this == VENDIDO || this == CANCELADO;
    }
}
