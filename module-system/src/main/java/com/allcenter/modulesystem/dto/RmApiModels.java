package com.allcenter.modulesystem.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class RmApiModels {

    private RmApiModels() {}

    public record Created(long id) {}

    public record EntradaListRow(
            Long id,
            Integer numeroRegistro,
            Long registroVehiculoId,
            LocalDate fecha,
            String hora,
            String tipoDocumento,
            String ocNumero,
            String numeroGuia,
            Long guiaInventarioId,
            String recepcionEstado,
            String motivoCancelacion,
            Instant canceladoAt,
            String canceladoPorEmail,
            String canceladoPorNombre,
            Instant createdAt,
            long lineas,
            String vehiculoPlaca,
            String vehiculoChofer,
            String vehiculoMarca,
            String vehiculoTipoRegistro) {}

    public record SalidaListRow(
            Long id,
            Integer numeroRegistro,
            Long registroVehiculoId,
            LocalDate fecha,
            String horaCabecera,
            Long transporteId,
            String ocNumero,
            String numeroGuia,
            Long guiaInventarioId,
            String recepcionEstado,
            String motivoCancelacion,
            Instant canceladoAt,
            String canceladoPorEmail,
            String canceladoPorNombre,
            Instant createdAt,
            long lineas,
            String vehiculoPlaca,
            String vehiculoChofer,
            String vehiculoMarca,
            String vehiculoTipoRegistro) {}

    public record VehiculoListRow(
            Long id,
            Integer numeroRegistro,
            String tipoRegistro,
            LocalDate fecha,
            String placa,
            String chofer,
            String marca,
            Instant createdAt) {}

    public record ActaListRow(
            Long id,
            String razonSocialNombre,
            String decision,
            String estado,
            String motivoCancelacion,
            Instant canceladoAt,
            String canceladoPorEmail,
            String canceladoPorNombre,
            Instant createdAt) {}

    public record EntradaDetalleResponse(
            Long id,
            String material,
            String cantidad,
            String unidad,
            String categoriaCodigo,
            String observaciones,
            List<String> photoUrls) {}

    public record RegistroEntradaResponse(
            Long id,
            Integer numeroRegistro,
            Long registroVehiculoId,
            LocalDate fecha,
            String hora,
            String tipoDocumento,
            String ocNumero,
            String numeroGuia,
            Long guiaInventarioId,
            String destino,
            String recepcionEstado,
            Instant validadoAt,
            String validadoPorEmail,
            Long choferValidacionEmpleadoId,
            String choferValidacionNombre,
            Instant createdAt,
            String createdByEmail,
            String observaciones,
            String motivoCancelacion,
            Instant canceladoAt,
            String canceladoPorEmail,
            String canceladoPorNombre,
            List<String> documentoPhotoUrls,
            List<EntradaDetalleResponse> detalles) {}

    public record SalidaDetalleResponse(
            Long id,
            String hora,
            String materialProducto,
            String cantidad,
            String unidad,
            String categoriaCodigo,
            String observaciones,
            List<String> photoUrls) {}

    public record RegistroSalidaResponse(
            Long id,
            Integer numeroRegistro,
            Long registroVehiculoId,
            LocalDate fecha,
            String horaCabecera,
            String origen,
            String destino,
            String numeroGuia,
            String ocNumero,
            Long guiaInventarioId,
            Long transporteId,
            Long choferSalidaEmpleadoId,
            String choferSalidaNombre,
            String recepcionEstado,
            Instant validadoAt,
            String validadoPorEmail,
            Long choferValidacionEmpleadoId,
            String choferValidacionNombre,
            Instant createdAt,
            String createdByEmail,
            String observaciones,
            String motivoCancelacion,
            Instant canceladoAt,
            String canceladoPorEmail,
            String canceladoPorNombre,
            List<String> cabeceraPhotoUrls,
            List<SalidaDetalleResponse> detalles) {}

    public record RegistroVehiculoResponse(
            Long id,
            Integer numeroRegistro,
            String tipoRegistro,
            LocalDate fecha,
            String horaIngreso,
            String marca,
            String placa,
            String chofer,
            String kilometraje,
            String horaSalida,
            Instant createdAt,
            String createdByEmail,
            List<String> photoUrls,
            List<EntradaListRow> entradas) {}

    public record NcTipoResponse(String tipo, boolean marcado, String detalle) {}

    public record ActaConformidadResponse(
            Long id,
            String razonSocialNombre,
            String guiaRemisionNum,
            String facturaOrdenCompraNum,
            String transportistaNombrePlaca,
            List<NcTipoResponse> tipos,
            String descripcionAmpliada,
            String decision,
            Integer cantidadConformeUnidades,
            String observacionesDecision,
            String estado,
            String motivoCancelacion,
            Instant canceladoAt,
            String canceladoPorEmail,
            String canceladoPorNombre,
            Instant createdAt,
            String createdByEmail,
            List<String> photoUrls) {}
}
