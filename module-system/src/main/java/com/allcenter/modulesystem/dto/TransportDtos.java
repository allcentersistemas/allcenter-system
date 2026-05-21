package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public class TransportDtos {

    public record ApiMessage(boolean success, String message) {}

    public record TransporteDto(
            Long transporteId,
            String placa,
            String numeroSerie,
            String modelo,
            String marca,
            String color,
            String descripcion,
            String tipoVehiculo,
            Double capacidad,
            Boolean activo,
            LocalDateTime fechaCreacion) {}

    public record CreateTransporteRequest(
            @NotBlank String placa,
            String numeroSerie,
            String modelo,
            String marca,
            String color,
            String descripcion,
            String tipoVehiculo,
            Double capacidad,
            Boolean activo) {}

    public record UpdateTransporteRequest(
            String placa,
            String numeroSerie,
            String modelo,
            String marca,
            String color,
            String descripcion,
            String tipoVehiculo,
            Double capacidad,
            Boolean activo) {}

    public record GuiaHeaderDto(
            Long guiaId,
            String numeroGuia,
            Long transporteId,
            String placa,
            String choferNombre,
            String choferDocumento,
            String estado,
            String notas,
            Integer totalPales,
            LocalDateTime fechaSalida,
            LocalDateTime fechaEntrega,
            LocalDateTime fechaCreacion) {}

    public record GuiaPaleLineDto(
            Long guiaPaleId,
            String codigo,
            Long paleId,
            String paleCodigo,
            Integer cantidad,
            String observacion,
            LocalDateTime fechaRegistro) {}

    public record GuiaResponse(GuiaHeaderDto guia, List<GuiaPaleLineDto> pales) {}

    public record CreateGuiaRequest(
            @NotNull Long transporteId,
            @NotBlank String numeroGuia,
            @NotBlank String choferNombre,
            String choferDocumento,
            String notas,
            LocalDateTime fechaSalida,
            Long creadoPor) {}

    public record UpdateGuiaRequest(
            String choferNombre,
            String choferDocumento,
            String estado,
            String notas,
            LocalDateTime fechaSalida,
            LocalDateTime fechaEntrega) {}

    public record AddGuiaPaleRequest(
            @NotNull Long paleId,
            String paleCodigo,
            @NotNull @Positive Integer cantidad,
            String observacion) {}

    /** Entrada de auditoría / trazabilidad de transporte (consulta paginada). */
    public record TransportAuditEntryDto(
            Long id,
            Instant occurredAt,
            String action,
            String entityType,
            String entityId,
            String correlationId,
            Long actorEmployeeId,
            String actorEmail,
            String clientIpPublic,
            String details) {}
}
