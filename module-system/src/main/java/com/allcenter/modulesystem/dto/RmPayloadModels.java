package com.allcenter.modulesystem.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

public final class RmPayloadModels {

    private RmPayloadModels() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EntradaDetalle(
            String proveedor,
            String ocNumero,
            String guiaNumero,
            String material,
            String colorModelo,
            String cantidadRecibida,
            String unidad,
            int fotosCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EntradaPayload(
            String fecha,
            String hora,
            Long transporteId,
            Integer cabeceraVehiculoFotosCount,
            Long choferIngresoEmpleadoId,
            String choferIngresoNombre,
            String kilometrajeIngreso,
            Boolean recepcionConformidadCerrada,
            Long choferValidacionEmpleadoId,
            String choferValidacionNombre,
            List<EntradaDetalle> detalles,
            Boolean generarRegistroVehiculo,
            String vehiculoMarca,
            String vehiculoPlaca) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SalidaDetalle(
            String hora,
            String destino,
            String noRqmVale,
            String noGuia,
            String materialProducto,
            String cantidad,
            String unidad,
            String recibeFirma,
            String entregaRci,
            int fotosCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SalidaPayload(
            String fecha,
            String hora,
            int cabeceraFotosCount,
            Long transporteId,
            Long choferSalidaEmpleadoId,
            String choferSalidaNombre,
            Boolean salidaConformidadCerrada,
            Long choferValidacionEmpleadoId,
            String choferValidacionNombre,
            List<SalidaDetalle> detalles) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VehiculoProducto(String materialProducto, String cantidad, String unidad) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VehiculoPayload(
            String fecha,
            String horaIngreso,
            String marca,
            String placa,
            String chofer,
            String kilometraje,
            String horaSalida,
            List<VehiculoProducto> productos,
            int fotosCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NcTipo(String tipo, boolean marcado, String detalle) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActaPayload(
            String razonSocialNombre,
            String guiaRemisionNum,
            String facturaOrdenCompraNum,
            String transportistaNombrePlaca,
            List<NcTipo> tipos,
            String descripcionAmpliada,
            String decision,
            Integer cantidadConformeUnidades,
            String observacionesDecision,
            int fotosCount) {}
}
