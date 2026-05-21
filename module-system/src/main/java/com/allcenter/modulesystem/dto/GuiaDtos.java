package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public final class GuiaDtos {

    private GuiaDtos() {}

    public record Created(long id) {}

    public record GuiaDetalleLineDto(
            Long id,
            Long paleId,
            String paleCodigo,
            String descripcion,
            String unidadMedida,
            String cantidad,
            LocalDateTime fechaRegistro) {}

    public record GuiaHeaderDto(
            Long guiaId,
            String numeroGuia,
            String estado,
            String notas,
            Long sucursalDestinoId,
            String sucursalDestinoNombre,
            Long ubicacionDestinoId,
            String ubicacionDestinoNombre,
            int totalLineas,
            LocalDateTime fechaCreacion) {}

    public record GuiaResponse(GuiaHeaderDto guia, List<GuiaDetalleLineDto> detalles) {}

    public record CreateGuiaRequest(
            String notas,
            Long destinationBranchId,
            Long destinationLocationId,
            Long creadoPor,
            List<Long> paleIds) {}

    public record UpdateGuiaRequest(String estado, String notas, Long destinationBranchId, Long destinationLocationId) {}

    public record AddGuiaDetalleManualRequest(
            @NotBlank String descripcion,
            @NotBlank String unidadMedida,
            @NotBlank String cantidad) {}

    public record AddGuiaDetallePaleRequest(@NotNull Long paleId) {}

    public record PaleEscaneadoRowDto(
            Long paleId,
            String codigo,
            String estado,
            String estadoEnvio,
            Integer cantidadPiezas,
            String ordenesResumen) {}
}
