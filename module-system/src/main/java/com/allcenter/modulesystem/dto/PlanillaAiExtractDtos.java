package com.allcenter.modulesystem.dto;

import java.util.List;

public final class PlanillaAiExtractDtos {

    private PlanillaAiExtractDtos() {}

    public record FeaturesResponse(boolean aiVisionEnabled) {}

    public record DetalleRow(
            String cantidad,
            String largo,
            String ancho,
            String l1,
            String l2,
            String a1,
            String a2,
            String ranuraDist,
            String ranuraProf,
            String ranuraEs,
            String ranuraLado,
            String descripcion) {}

    public record ExtractResponse(List<DetalleRow> filas, String provider, String model) {}
}
