package com.allcenter.modulesystem.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ImpresionStickerResponse(
        Long impresionId,
        Long usuarioId,
        String usuarioEmail,
        Long orderId,
        String metodo,
        String equipo,
        String ubicacion,
        String direccionIp,
        String userAgent,
        Integer cantidadEtiquetas,
        String observaciones,
        OffsetDateTime fecha,
        List<Detalle> detalles) {

    public record Detalle(
            Long impresionDetalleId,
            Long partId,
            Long piezaId,
            Integer numeroPieza,
            String codigoQr,
            String snapshot,
            OffsetDateTime fecha) {}
}
