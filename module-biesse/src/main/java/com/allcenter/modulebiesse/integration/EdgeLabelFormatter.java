package com.allcenter.modulebiesse.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Cantos U/B/I/D para etiqueta ZPL (alineado al agente). */
public final class EdgeLabelFormatter {

    private EdgeLabelFormatter() {}

    public static String format(String edgeUp, String edgeLo, String edgeL, String edgeR) {
        List<String> bands = new ArrayList<>(4);
        addEdge(bands, "U", edgeUp);
        addEdge(bands, "B", edgeLo);
        addEdge(bands, "I", edgeL);
        addEdge(bands, "D", edgeR);
        if (bands.isEmpty()) {
            return "";
        }
        String text = String.join(" ", bands);
        return text.length() <= 18 ? text : text.substring(0, 17) + ".";
    }

    private static void addEdge(List<String> bands, String prefix, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String t = value.trim();
        if ("0".equals(t)
                || "NONE".equalsIgnoreCase(t)
                || "N/A".equalsIgnoreCase(t)) {
            return;
        }
        bands.add(prefix + ":" + abbrev(t));
    }

    private static String abbrev(String text) {
        String t = text.trim().toUpperCase(Locale.ROOT);
        return t.length() <= 6 ? t : t.substring(0, 6);
    }
}
