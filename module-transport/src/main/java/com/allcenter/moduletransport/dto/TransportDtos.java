package com.allcenter.moduletransport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

    public record TransporteCargaHeaderDto(
            Long transporteCargaId,
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

    public record TransporteCargaDetalleDto(
            Long transporteCargaDetalleId,
            Long paleEnvioId,
            String paleCodigo,
            Integer cantidad,
            String observacion,
            LocalDateTime fechaRegistro) {}

    public record TransporteCargaResponse(
            TransporteCargaHeaderDto carga,
            List<TransporteCargaDetalleDto> detalles) {}

    public record CreateTransporteCargaRequest(
            @NotNull Long transporteId,
            @NotBlank String choferNombre,
            String choferDocumento,
            String notas,
            LocalDateTime fechaSalida,
            Long creadoPor) {}

    public record UpdateTransporteCargaRequest(
            String choferNombre,
            String choferDocumento,
            String estado,
            String notas,
            LocalDateTime fechaSalida,
            LocalDateTime fechaEntrega) {}

    public record AddTransporteCargaDetalleRequest(
            @NotNull Long paleEnvioId,
            String paleCodigo,
            @NotNull @Positive Integer cantidad,
            String observacion) {}
}
