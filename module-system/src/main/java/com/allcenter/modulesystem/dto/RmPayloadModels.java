package com.allcenter.modulesystem.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

public final class RmPayloadModels {

    private RmPayloadModels() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EntradaDetalle(String material, String cantidad, String unidad, int fotosCount) {}

    /** Vehículo en borrador + documento; un solo envío al validar (Android). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IngresoCompletoPayload(
            VehiculoPayload vehiculo,
            EntradaPayload entrada) {}

    /** Vehículo en borrador + salida; un solo envío al validar (Android). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SalidaCompletoPayload(
            VehiculoPayload vehiculo,
            SalidaPayload salida) {}

    /** Documento (OC + guía) asociado a un {@link com.allcenter.modulesystem.model.RmRegistroVehiculo}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EntradaPayload(
            Long registroVehiculoId,
            String fecha,
            String hora,
            String destino,
            String ocNumero,
            String guiaNumero,
            Long guiaInventarioId,
            int documentoFotosCount,
            Boolean recepcionConformidadCerrada,
            Long choferValidacionEmpleadoId,
            String choferValidacionNombre,
            List<EntradaDetalle> detalles,
            /** Contraseña del chofer que valida; obligatoria si recepcionConformidadCerrada. */
            String confirmPassword) {}

    /** Línea de salida: solo ítems (cabecera lleva destino, guía y OC). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SalidaDetalle(String hora, String materialProducto, String cantidad, String unidad, int fotosCount) {}

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
            String destino,
            String numeroGuia,
            String ordenCompra,
            Long guiaInventarioId,
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
            /** {@code ingreso} o {@code salida}. */
            String tipoRegistro,
            int fotosCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NcTipo(String tipo, boolean marcado, String detalle) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActaPayload(
            String razonSocialNombre,
            String guiaRemisionNum,
            String facturaOrdenCompraNum,
            Long transporteId,
            String choferNombre,
            String transportistaNombrePlaca,
            List<NcTipo> tipos,
            String descripcionAmpliada,
            String decision,
            Integer cantidadConformeUnidades,
            String observacionesDecision,
            int fotosCount) {}
}
