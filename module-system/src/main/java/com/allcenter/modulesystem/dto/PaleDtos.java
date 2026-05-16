package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public class PaleDtos {

    public record SucursalDto(
            Long id, String nombre, String direccion, String ciudad, String departamento) {}

    public record UbicacionDto(
            Long id,
            String nombre,
            String direccion,
            String distrito,
            String departamento,
            String ciudad) {}

    public record CatalogDto(List<SucursalDto> branches, List<UbicacionDto> locations) {}

    public record CreateSucursalRequest(
            @NotNull String nombre,
            String direccion,
            String ciudad,
            String departamento) {}

    public record CreateUbicacionRequest(
            @NotNull String nombre,
            String direccion,
            String distrito,
            String departamento,
            String ciudad) {}

    public record CreatePaleRequest(
            String code,
            @NotNull Long branchId,
            Long originLocationId,
            /** Obligatorio si {@code destinationLocationId} es null. */
            Long destinationBranchId,
            /** Si viene, el destino es obra/ubicación (puede combinarse con sucursal o solo obra). */
            Long destinationLocationId,
            String notes,
            Long createdBy) {}

    public record PaleHeaderDto(
            Long paleenvioid,
            String codigo,
            String estado,
            Integer cantidadPiezas,
            Integer cantidadOrdenes,
            String ordenesResumen,
            String notas,
            Long sucursalOrigenId,
            String sucursalOrigenNombre,
            Long sucursalDestinoId,
            String sucursalDestinoNombre,
            Long ubicacionOrigenId,
            Long ubicacionDestinoId,
            String ubicacionDestinoNombre,
            LocalDateTime fechaCreacion,
            LocalDateTime fechaCierre) {}

    public record PaleDetailItemDto(
            Long paleenviodetalleid,
            Long piezaId,
            Long partId,
            Long orderId,
            String orderName,
            String partCode,
            Integer numeroPieza,
            LocalDateTime fechaAgregado,
            /** De tabla {@code partes} (descripcion / descripcion1), mismo esquema que servicio_sincronizacion. */
            String partDescripcion,
            String partDescripcion1,
            /** Cantidad programada en partes (denominador para p.ej. 2/7). */
            Integer piezasPlanParte,
            /** Longitud × ancho desde {@code partes} (texto compacto). */
            String medida) {}

    public record PaleDetailResponse(PaleHeaderDto pallet, List<PaleDetailItemDto> details) {}

    public record ScanPieceToPaleRequest(@NotNull Long pieceId, Long addedBy) {}

    public record ClosePaleRequest(String notes) {}

    public record UpdatePaleRequest(
            String code,
            String estado,
            Long branchId,
            Long originLocationId,
            Long destinationBranchId,
            Long destinationLocationId,
            String notes) {}

    public record PaleAuditEntryDto(
            Long id,
            LocalDateTime occurredAt,
            String action,
            String entityType,
            String entityId,
            Long paleId,
            String paleCodigo,
            String details,
            Long actorEmployeeId,
            String actorEmail,
            String sourceIp,
            String userAgent) {}

    public record ApiMessage(boolean success, String message) {}
}
