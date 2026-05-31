package com.allcenter.modulesystem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

/** Respuesta GET/POST /api/impresion/sticker (nombres JSON alineados con la UI del portal). */
public record ImpresionStickerResponse(
        @JsonProperty("id") Long impresionId,
        @JsonProperty("usuarioId") Long usuarioId,
        @JsonProperty("printedByEmail") String usuarioEmail,
        @JsonProperty("orderId") Long orderId,
        String metodo,
        String equipo,
        String ubicacion,
        @JsonProperty("direccionIp") String direccionIp,
        @JsonProperty("userAgent") String userAgent,
        @JsonProperty("pieceCount") Integer cantidadEtiquetas,
        @JsonProperty("notes") String observaciones,
        @JsonProperty("printedAt") OffsetDateTime fecha,
        @JsonProperty("partId") Long partId,
        List<Detalle> detalles) {

    public record Detalle(
            @JsonProperty("id") Long impresionDetalleId,
            @JsonProperty("partId") Long partId,
            @JsonProperty("piezaId") Long piezaId,
            @JsonProperty("numeroPieza") Integer numeroPieza,
            @JsonProperty("codigoQr") String codigoQr,
            String snapshot,
            OffsetDateTime fecha) {}
}
