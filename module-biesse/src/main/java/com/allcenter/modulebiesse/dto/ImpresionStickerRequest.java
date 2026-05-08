package com.allcenter.modulebiesse.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ImpresionStickerRequest(
        @NotNull Long orderId,
        String metodo,
        String equipo,
        String ubicacion,
        String userAgent,
        String observaciones,
        @NotEmpty @Valid List<Detalle> detalles) {

    public record Detalle(
            Long partId,
            Long piezaId,
            Integer numeroPieza,
            String codigoQr,
            String snapshot) {}
}
