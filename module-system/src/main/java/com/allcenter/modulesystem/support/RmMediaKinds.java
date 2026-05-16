package com.allcenter.modulesystem.support;

import java.util.Locale;
import java.util.Set;

public final class RmMediaKinds {

    public static final String ENTRADA_DETALLE = "entrada-detalle";
    /** Fotos del vehículo al ingreso (cabecera del registro de entrada RM). */
    public static final String ENTRADA_CABECERA_VEHICULO = "entrada-cabecera-vehiculo";
    public static final String SALIDA_CABECERA = "salida-cabecera";
    public static final String SALIDA_DETALLE = "salida-detalle";
    public static final String ACTA = "acta";
    public static final String VEHICULO = "vehiculo";

    private static final Set<String> ALL =
            Set.of(
                    ENTRADA_DETALLE,
                    ENTRADA_CABECERA_VEHICULO,
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
        return kind.trim().toLowerCase(Locale.ROOT);
    }
}
