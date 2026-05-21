package com.allcenter.modulesystem.support;

import java.util.Locale;

/** Genera códigos G-{numeroGuia} para {@link com.allcenter.modulesystem.model.GuiaPale}. */
public final class GuiaPaleCodigo {

    private GuiaPaleCodigo() {}

    public static String normalizeNumeroGuia(String numeroGuia) {
        if (numeroGuia == null) {
            return "";
        }
        return numeroGuia.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * @param lineIndex 1-based; si hay un solo pale en la guía use 1 → {@code G-NG123}; con varios → {@code G-NG123-2}.
     */
    public static String build(String numeroGuia, int lineIndex) {
        String base = normalizeNumeroGuia(numeroGuia).replaceAll("\\s+", "");
        if (base.isEmpty()) {
            throw new IllegalArgumentException("numeroGuia vacio");
        }
        if (lineIndex <= 1) {
            return "G-" + base;
        }
        return "G-" + base + "-" + lineIndex;
    }
}
