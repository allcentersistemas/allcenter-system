package com.allcenter.modulesystem.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {
    }

    public record ProyectoPayload(
            String nombre,
            String cliente,
            String referencia,
            String descripcion,
            Long maquinaId
    ) {
    }

    public record OrdenPayload(
            String codigo,
            String descripcion
    ) {
    }

    public record DetallePayload(
            String tablero,
            String cantidad,
            String largoVeta,
            String ancho,
            String veta,
            String l1,
            String l2,
            String a1,
            String a2,
            String perforacionCantidad,
            String perforacionLado1,
            String perforacionLado2,
            String ranuraDist,
            String ranuraProf,
            String ranuraEs,
            String ranuraLado,
            boolean ranuraEspecial,
            boolean observado,
            String observacion
    ) {
    }

    public record OrdenCompuestaPayload(
            String codigo,
            String descripcion,
            List<DetallePayload> detalles
    ) {
    }

    public record ProyectoCompuestoPayload(
            Long projectId,
            ProyectoPayload project,
            List<OrdenCompuestaPayload> orders
    ) {
    }

    public record ProyectoResumenResponse(
            Long id,
            Long codigoProyecto,
            String nombre,
            String descripcion,
            String cliente,
            String estado,
            Long vendedorId,
            String vendedorNombre,
            LocalDateTime fechaCreacion,
            int cantidadOrdenes,
            boolean editable,
            Long maquinaId,
            String maquinaParametros,
            boolean tieneCotizacion,
            String cotizacionArchivo,
            boolean tienePlano,
            String planoArchivo,
            ProyectoEstadoTiempos estadoTiempos
    ) {
    }

    public record ProyectoResponse(
            Long id,
            Long codigoProyecto,
            String nombre,
            String cliente,
            Long clientUserId,
            String referencia,
            String descripcion,
            String estado,
            Long vendedorId,
            String vendedorNombre,
            LocalDateTime fechaCreacion,
            boolean editable,
            Long maquinaId,
            String maquinaParametros,
            String cotizacionArchivo,
            String planoArchivo,
            ProyectoEstadoTiempos estadoTiempos
    ) {
    }

    public record OrdenResponse(
            Long id,
            Long proyectoId,
            String codigo,
            String descripcion
    ) {
    }

    public record DetalleResponse(
            Long id,
            Long ordenId,
            String tablero,
            String cantidad,
            String largoVeta,
            String ancho,
            String veta,
            String l1,
            String l2,
            String a1,
            String a2,
            String perforacionCantidad,
            String perforacionLado1,
            String perforacionLado2,
            String ranuraDist,
            String ranuraProf,
            String ranuraEs,
            String ranuraLado,
            boolean ranuraEspecial,
            boolean observado,
            String observacion
    ) {
    }

    public record OrdenConDetallesResponse(
            Long id,
            Long proyectoId,
            String codigo,
            String descripcion,
            List<DetalleResponse> detalles
    ) {
    }

    public record ProyectoEstadoPayload(
            String estado
    ) {
    }

    public record ProyectoGestionPayload(
            String nombre,
            String cliente,
            Long clientUserId,
            String referencia,
            String descripcion,
            Long vendedorId,
            Long maquinaId
    ) {
    }

    public record ProyectoEstadoTiempos(
            LocalDateTime enviado,
            LocalDateTime enAtencion,
            LocalDateTime cotizado,
            LocalDateTime vendido,
            LocalDateTime produccion,
            LocalDateTime despacho,
            LocalDateTime listoParaEntregar,
            LocalDateTime entregado,
            LocalDateTime cancelado
    ) {
    }

    public record ProyectoMaquinaPayload(
            Long maquinaId
    ) {
    }

    public record MaquinaPayload(
            String codigo,
            String nombre,
            Boolean activo
    ) {
    }

    public record MaquinaResponse(
            Long id,
            String codigo,
            String nombre,
            boolean activo
    ) {
    }

    public record ProyectoConOrdenesResponse(
            ProyectoResponse project,
            List<OrdenConDetallesResponse> orders
    ) {
    }

    public record AndroidScanProgressPayload(
            String orderName,
            String bookingCode,
            Boolean orderComplete
    ) {
    }

    public record AndroidOrderRefPayload(
            String orderName,
            String bookingCode
    ) {
    }

    public record FulfillmentActionResponse(
            boolean success,
            String message,
            Long proyectoId
    ) {
    }
}
