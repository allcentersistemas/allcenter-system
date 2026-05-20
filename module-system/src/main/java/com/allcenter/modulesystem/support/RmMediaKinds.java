package com.allcenter.modulesystem.support;

import java.util.Locale;
import java.util.Set;

public final class RmMediaKinds {

    public static final String ENTRADA_DETALLE = "entrada-detalle";
    /** Fotos del documento OC/NG (registro de entrada RM). */
    public static final String ENTRADA_DOCUMENTO = "entrada-documento";
    /** Alias legacy (misma ruta de almacenamiento que {@link #ENTRADA_DOCUMENTO}). */
    @Deprecated
    public static final String ENTRADA_CABECERA_VEHICULO = ENTRADA_DOCUMENTO;
    public static final String SALIDA_CABECERA = "salida-cabecera";
    public static final String SALIDA_DETALLE = "salida-detalle";
    public static final String ACTA = "acta";
    public static final String VEHICULO = "vehiculo";

    private static final Set<String> ALL =
            Set.of(
                    ENTRADA_DETALLE,
                    ENTRADA_DOCUMENTO,
                    SALIDA_CABECERA,
                    SALIDA_DETALLE,
                    ACTA,
                    VEHICULO);

    private RmMediaKinds() {}

    public static void requireKnown(String kind) {
        if (kind == null || kind.isBlank() || !ALL.contains(kind.trim())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Tipo de media no valido");
        }
    }

    public static String normalize(String kind) {
        if (kind == null) {
            return "";
        }
        String k = kind.trim().toLowerCase(Locale.ROOT);
        if ("entrada-cabecera-vehiculo".equals(k)) {
            return ENTRADA_DOCUMENTO;
        }
        return k;
    }
}
