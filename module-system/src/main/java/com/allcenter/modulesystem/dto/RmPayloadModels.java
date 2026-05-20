package com.allcenter.modulesystem.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

public final class RmPayloadModels {

    private RmPayloadModels() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EntradaDetalle(
            String proveedor,
            String material,
            String colorModelo,
            String cantidadRecibida,
            String unidad,
            int fotosCount) {}

    /** Documento (OC o NG) asociado a un {@link com.allcenter.modulesystem.model.RmRegistroVehiculo} ya registrado. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EntradaPayload(
            Long registroVehiculoId,
            String fecha,
            String hora,
            /** OC o NG */
            String tipoDocumento,
            String ocNumero,
            String guiaNumero,
            int documentoFotosCount,
            Boolean recepcionConformidadCerrada,
            Long choferValidacionEmpleadoId,
            String choferValidacionNombre,
            List<EntradaDetalle> detalles,
            /** Contraseña del chofer que valida; obligatoria si recepcionConformidadCerrada. */
            String confirmPassword) {}

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
            List<SalidaDetalle> detalles,
            String confirmPassword) {}

    /** Registro de ingreso del vehiculo (paso 1 del flujo de entradas). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VehiculoPayload(
            String fecha,
            String horaIngreso,
            String marca,
            String placa,
            String chofer,
            String kilometraje,
            String horaSalida,
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
