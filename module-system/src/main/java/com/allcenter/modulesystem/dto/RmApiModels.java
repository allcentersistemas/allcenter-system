package com.allcenter.modulesystem.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class RmApiModels {

    private RmApiModels() {}

    public record Created(long id) {}

    public record CreatedEntrada(long id, Long vehiculoId) {}

    public record EntradaListRow(
            Long id,
            LocalDate fecha,
            String hora,
            Long transporteId,
            String recepcionEstado,
            Instant createdAt,
            long lineas) {}

    public record SalidaListRow(
            Long id,
            LocalDate fecha,
            String horaCabecera,
            Long transporteId,
            String recepcionEstado,
            Instant createdAt,
            long lineas) {}

    public record VehiculoListRow(
            Long id, LocalDate fecha, String placa, String chofer, String marca, Instant createdAt) {}

    public record ActaListRow(Long id, String razonSocialNombre, String decision, Instant createdAt) {}

    public record EntradaDetalleResponse(
            Long id,
            String proveedor,
            String ocNumero,
            String guiaNumero,
            String material,
            String colorModelo,
            String cantidadRecibida,
            String unidad,
            List<String> photoUrls) {}

    public record RegistroEntradaResponse(
            Long id,
            LocalDate fecha,
            String hora,
            Long transporteId,
            Long choferIngresoEmpleadoId,
            String choferIngresoNombre,
            String kilometrajeIngreso,
            String recepcionEstado,
            Instant validadoAt,
            String validadoPorEmail,
            Long choferValidacionEmpleadoId,
            String choferValidacionNombre,
            Instant createdAt,
            String createdByEmail,
            List<String> cabeceraVehiculoPhotoUrls,
            List<EntradaDetalleResponse> detalles) {}

    public record SalidaDetalleResponse(
            Long id,
            String hora,
            String destino,
            String noRqmVale,
            String noGuia,
            String materialProducto,
            String cantidad,
            String unidad,
            String recibeFirma,
            String entregaRci,
            List<String> photoUrls) {}

    public record RegistroSalidaResponse(
            Long id,
            LocalDate fecha,
            String horaCabecera,
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
            List<String> cabeceraPhotoUrls,
            List<SalidaDetalleResponse> detalles) {}

    public record RegistroVehiculoResponse(
            Long id,
            LocalDate fecha,
            String horaIngreso,
            String marca,
            String placa,
            String chofer,
            String kilometraje,
            String horaSalida,
            Instant createdAt,
            String createdByEmail,
            List<VehiculoProductoResponse> productos,
            List<String> photoUrls) {}

    public record VehiculoProductoResponse(String materialProducto, String cantidad, String unidad) {}

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
            Instant createdAt,
            String createdByEmail,
            List<String> photoUrls) {}
}
