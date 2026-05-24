package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.GuiaDtos;
import com.allcenter.modulesystem.dto.GuiaDtos.AddGuiaDetalleManualRequest;
import com.allcenter.modulesystem.dto.GuiaDtos.AddGuiaDetallePaleRequest;
import com.allcenter.modulesystem.dto.GuiaDtos.CreateGuiaRequest;
import com.allcenter.modulesystem.dto.GuiaDtos.GuiaDetalleLineDto;
import com.allcenter.modulesystem.dto.GuiaDtos.GuiaHeaderDto;
import com.allcenter.modulesystem.dto.GuiaDtos.GuiaResponse;
import com.allcenter.modulesystem.dto.GuiaDtos.PaleEscaneadoRowDto;
import com.allcenter.modulesystem.dto.GuiaDtos.UpdateGuiaRequest;
import com.allcenter.modulesystem.model.Guia;
import com.allcenter.modulesystem.model.Guiadetalle;
import com.allcenter.modulesystem.model.Pale;
import com.allcenter.modulesystem.model.Sucursal;
import com.allcenter.modulesystem.model.Ubicacion;
import com.allcenter.modulesystem.repository.GuiaRepository;
import com.allcenter.modulesystem.repository.GuiadetalleRepository;
import com.allcenter.modulesystem.repository.PaleRepository;
import com.allcenter.modulesystem.repository.SucursalRepository;
import com.allcenter.modulesystem.repository.UbicacionRepository;
import com.allcenter.modulesystem.support.GuiaNumeroGenerator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class GuiaInventoryService {

    private static final String ESTADO_CREADA = "CREADA";
    private static final String ESTADO_EN_CAMINO = "EN_CAMINO";
    private static final String ESTADO_BORRADOR = "BORRADOR";
    private static final String ESTADO_CERRADA = "CERRADA";
    private static final String ESTADO_ENTREGADO = "ENTREGADO";
    private static final String ESTADO_ENVIO_ESCANEADO = "ESCANEADO";
    private static final String UNIDAD_PIEZAS = "piezas";

    private final GuiaRepository guiaRepository;
    private final GuiadetalleRepository detalleRepository;
    private final PaleRepository paleRepository;
    private final SucursalRepository sucursalRepository;
    private final UbicacionRepository ubicacionRepository;

    @Transactional(readOnly = true)
    public List<GuiaHeaderDto> listGuias() {
        return listGuias(null);
    }

    @Transactional(readOnly = true)
    public List<GuiaHeaderDto> listGuias(String estadoFilter) {
        String estado = trimOptional(estadoFilter);
        return guiaRepository.findAllForList().stream()
                .filter(g -> estado == null || estado.equalsIgnoreCase(g.getEstado()))
                .map(this::toHeader)
                .toList();
    }

    @Transactional
    public void markGuiaCerrada(long guiaId) {
        Guia guia =
                guiaRepository
                        .findById(guiaId)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Guia no encontrada"));
        guia.setEstado(ESTADO_CERRADA);
        guiaRepository.save(guia);
    }

    /** Recepción RM validada: la guía en camino queda entregada en destino. */
    @Transactional
    public void markGuiaEntregada(long guiaId) {
        Guia guia =
                guiaRepository
                        .findById(guiaId)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Guia no encontrada"));
        String actual = guia.getEstado() != null ? guia.getEstado().trim() : "";
        if (!ESTADO_EN_CAMINO.equalsIgnoreCase(actual)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Solo se puede marcar entregada una guia en estado EN_CAMINO. Actual: " + actual);
        }
        guia.setEstado(ESTADO_ENTREGADO);
        guiaRepository.save(guia);
    }

    public void markGuiaEnCamino(long guiaId) {
        Guia guia =
                guiaRepository
                        .findById(guiaId)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Guia no encontrada"));
        String actual = guia.getEstado() != null ? guia.getEstado().trim() : "";
        if (!ESTADO_CREADA.equalsIgnoreCase(actual) && !ESTADO_BORRADOR.equalsIgnoreCase(actual)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Solo se puede marcar en camino una guia en estado CREADA. Actual: " + actual);
        }
        guia.setEstado(ESTADO_EN_CAMINO);
        guiaRepository.save(guia);
    }

    @Transactional(readOnly = true)
    public GuiaResponse getGuia(long id) {
        Guia guia =
                guiaRepository
                        .findByIdWithDetalles(id)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Guia no encontrada"));
        return toResponse(guia);
    }

    @Transactional
    public GuiaResponse createGuia(CreateGuiaRequest request, Long originBranchId) {
        if (originBranchId == null || originBranchId <= 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "El usuario no tiene sucursal asignada; no se puede definir el origen de la guia");
        }
        List<Long> paleIds = request.paleIds() != null ? request.paleIds() : List.of();
        if (paleIds.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Agregue al menos un pale escaneado a la guia");
        }
        Set<Long> seen = new HashSet<>();
        for (Long paleId : paleIds) {
            if (paleId == null || paleId <= 0) {
                throw new ResponseStatusException(BAD_REQUEST, "Pale invalido en la lista");
            }
            if (!seen.add(paleId)) {
                throw new ResponseStatusException(CONFLICT, "No repita el mismo pale en la guia");
            }
        }
        List<Pale> pales = new ArrayList<>();
        for (Long paleId : paleIds) {
            Pale pale =
                    paleRepository
                            .findById(paleId)
                            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Pale no encontrado"));
            if (!isEstadoEnvioEscaneado(pale.getEstadoEnvio())) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "Solo se pueden agregar pales con estado de envio ESCANEADO. Actual: "
                                + pale.getEstadoEnvio());
            }
            if (Boolean.TRUE.equals(pale.getEnGuia())) {
                throw new ResponseStatusException(
                        CONFLICT, "El pale " + pale.getCodigo() + " ya esta asignado a una guia");
            }
            pales.add(pale);
        }

        long max = guiaRepository.findMaxCorrelativoSequence();
        String numero = GuiaNumeroGenerator.format(GuiaNumeroGenerator.nextSequence(max));

        Guia guia = new Guia();
        guia.setNumeroGuia(numero);
        guia.setEstado(ESTADO_CREADA);
        guia.setNotas(trimOptional(request.notas()));
        guia.setOrdenCompra(trimOptional(request.ordenCompra()));
        applyOrigen(guia, originBranchId);
        if (request.destinationBranchId() != null || request.destinationLocationId() != null) {
            applyDestino(guia, request.destinationBranchId(), request.destinationLocationId());
        }
        guia.setCreadoPor(request.creadoPor());
        guia.setFechaCreacion(LocalDateTime.now());
        guia = guiaRepository.save(guia);

        for (Pale pale : pales) {
            Guiadetalle row = buildDetalleFromPale(guia, pale);
            detalleRepository.save(row);
            markPaleEnGuia(pale, true);
            if (guia.getDetalles() == null) {
                guia.setDetalles(new ArrayList<>());
            }
            guia.getDetalles().add(row);
        }
        return toResponse(guia);
    }

    @Transactional
    public GuiaResponse updateGuia(long id, UpdateGuiaRequest request) {
        Guia guia =
                guiaRepository
                        .findByIdWithDetalles(id)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Guia no encontrada"));
        ensureEditable(guia);
        if (request.estado() != null) {
            guia.setEstado(normalizeEstado(request.estado()));
        }
        if (request.notas() != null) {
            guia.setNotas(trimOptional(request.notas()));
        }
        if (request.ordenCompra() != null) {
            guia.setOrdenCompra(trimOptional(request.ordenCompra()));
        }
        if (request.destinationBranchId() != null || request.destinationLocationId() != null) {
            applyDestino(guia, request.destinationBranchId(), request.destinationLocationId());
        }
        guia = guiaRepository.save(guia);
        return toResponse(guia);
    }

    @Transactional
    public GuiaResponse addDetalleManual(long guiaId, AddGuiaDetalleManualRequest request) {
        Guia guia =
                guiaRepository
                        .findByIdWithDetalles(guiaId)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Guia no encontrada"));
        ensureEditable(guia);
        Guiadetalle row = new Guiadetalle();
        row.setGuia(guia);
        row.setPaleId(null);
        row.setDescripcion(trimRequired(request.descripcion(), "descripcion"));
        row.setUnidadMedida(trimRequired(request.unidadMedida(), "unidadMedida"));
        row.setCantidad(trimRequired(request.cantidad(), "cantidad"));
        row.setFechaRegistro(LocalDateTime.now());
        detalleRepository.save(row);
        guia.getDetalles().add(row);
        return toResponse(guia);
    }

    @Transactional
    public GuiaResponse addDetalleFromPale(long guiaId, AddGuiaDetallePaleRequest request) {
        Guia guia =
                guiaRepository
                        .findByIdWithDetalles(guiaId)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Guia no encontrada"));
        ensureEditable(guia);
        attachPaleToGuia(guia, request.paleId());
        return toResponse(guia);
    }

    private void attachPaleToGuia(Guia guia, long paleId) {
        if (detalleRepository.existsByGuiaIdAndPaleId(guia.getId(), paleId)) {
            throw new ResponseStatusException(CONFLICT, "Ese pale ya esta en la guia");
        }
        Pale pale =
                paleRepository
                        .findById(paleId)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Pale no encontrado"));
        if (!isEstadoEnvioEscaneado(pale.getEstadoEnvio())) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Solo se pueden agregar pales con estado de envio ESCANEADO. Actual: "
                            + pale.getEstadoEnvio());
        }
        if (Boolean.TRUE.equals(pale.getEnGuia())) {
            throw new ResponseStatusException(
                    CONFLICT, "El pale " + pale.getCodigo() + " ya esta asignado a otra guia");
        }
        Guiadetalle row = buildDetalleFromPale(guia, pale);
        detalleRepository.save(row);
        markPaleEnGuia(pale, true);
        if (guia.getDetalles() == null) {
            guia.setDetalles(new ArrayList<>());
        }
        guia.getDetalles().add(row);
    }

    private Guiadetalle buildDetalleFromPale(Guia guia, Pale pale) {
        Guiadetalle row = new Guiadetalle();
        row.setGuia(guia);
        row.setPaleId(pale.getId());
        row.setDescripcion(buildPaleDescripcion(pale));
        row.setUnidadMedida(UNIDAD_PIEZAS);
        row.setCantidad(String.valueOf(pale.getCantidadPiezas() != null ? pale.getCantidadPiezas() : 0));
        row.setFechaRegistro(LocalDateTime.now());
        return row;
    }

    @Transactional
    public GuiaResponse removeDetalle(long guiaId, long detalleId) {
        Guia guia =
                guiaRepository
                        .findByIdWithDetalles(guiaId)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Guia no encontrada"));
        ensureEditable(guia);
        Guiadetalle row =
                detalleRepository
                        .findById(detalleId)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Linea no encontrada"));
        if (!row.getGuia().getId().equals(guiaId)) {
            throw new ResponseStatusException(BAD_REQUEST, "La linea no pertenece a la guia");
        }
        Long paleId = row.getPaleId();
        guia.getDetalles().remove(row);
        detalleRepository.delete(row);
        if (paleId != null) {
            paleRepository.findById(paleId).ifPresent(p -> markPaleEnGuia(p, false));
        }
        return toResponse(guia);
    }

    @Transactional(readOnly = true)
    public List<PaleEscaneadoRowDto> listPalesEscaneados(String codigoQuery) {
        String q = trimOptional(codigoQuery);
        return paleRepository.findAll().stream()
                .filter(p -> isEstadoEnvioEscaneado(p.getEstadoEnvio()))
                .filter(p -> !Boolean.TRUE.equals(p.getEnGuia()))
                .filter(p -> q == null || matchesCodigoQuery(p.getCodigo(), q))
                .sorted((a, b) -> b.getFechaCreacion().compareTo(a.getFechaCreacion()))
                .map(this::toPaleEscaneado)
                .toList();
    }

    private static boolean matchesCodigoQuery(String codigo, String query) {
        if (codigo == null) {
            return false;
        }
        return codigo.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private static boolean isEstadoEnvioEscaneado(String estadoEnvio) {
        return estadoEnvio != null && ESTADO_ENVIO_ESCANEADO.equalsIgnoreCase(estadoEnvio.trim());
    }

    private static String buildPaleDescripcion(Pale pale) {
        String resumen = pale.getOrdenesResumen() != null ? pale.getOrdenesResumen().trim() : "";
        if (!resumen.isEmpty()) {
            return resumen;
        }
        String codigo = pale.getCodigo() != null ? pale.getCodigo().trim() : "";
        if (!codigo.isEmpty()) {
            return codigo;
        }
        return "Pale " + pale.getId();
    }

    private void ensureEditable(Guia guia) {
        String e = guia.getEstado() != null ? guia.getEstado().trim() : "";
        if (ESTADO_CERRADA.equalsIgnoreCase(e)
                || ESTADO_EN_CAMINO.equalsIgnoreCase(e)
                || ESTADO_ENTREGADO.equalsIgnoreCase(e)) {
            throw new ResponseStatusException(BAD_REQUEST, "La guia no admite cambios en su estado actual");
        }
    }

    private void applyOrigen(Guia guia, Long originBranchId) {
        Sucursal sucursal =
                sucursalRepository
                        .findById(originBranchId)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Sucursal origen no encontrada"));
        guia.setSucursalOrigen(sucursal);
        guia.setUbicacionOrigen(null);
    }

    private void applyDestino(Guia guia, Long destinationBranchId, Long destinationLocationId) {
        if (destinationLocationId != null) {
            Ubicacion ubicacion =
                    ubicacionRepository
                            .findById(destinationLocationId)
                            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ubicacion destino no encontrada"));
            guia.setUbicacionDestino(ubicacion);
            if (destinationBranchId != null) {
                Sucursal sucursal =
                        sucursalRepository
                                .findById(destinationBranchId)
                                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Sucursal destino no encontrada"));
                guia.setSucursalDestino(sucursal);
            } else {
                guia.setSucursalDestino(null);
            }
            return;
        }
        if (destinationBranchId == null) {
            guia.setSucursalDestino(null);
            guia.setUbicacionDestino(null);
            return;
        }
        Sucursal sucursal =
                sucursalRepository
                        .findById(destinationBranchId)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Sucursal destino no encontrada"));
        guia.setSucursalDestino(sucursal);
        guia.setUbicacionDestino(null);
    }

    private String normalizeEstado(String estado) {
        String e = trimRequired(estado, "estado").toUpperCase(Locale.ROOT);
        if (!ESTADO_CREADA.equals(e)
                && !ESTADO_EN_CAMINO.equals(e)
                && !ESTADO_BORRADOR.equals(e)
                && !ESTADO_CERRADA.equals(e)
                && !ESTADO_ENTREGADO.equals(e)) {
            throw new ResponseStatusException(BAD_REQUEST, "Estado de guia no valido");
        }
        return e;
    }

    private GuiaResponse toResponse(Guia guia) {
        List<GuiaDetalleLineDto> lines =
                detalleRepository.findByGuiaIdOrderByIdAsc(guia.getId()).stream()
                        .map(this::toDetalleLine)
                        .toList();
        return new GuiaResponse(toHeader(guia, lines.size()), lines);
    }

    private GuiaHeaderDto toHeader(Guia guia) {
        return toHeader(guia, guia.getDetalles() != null ? guia.getDetalles().size() : 0);
    }

    private GuiaHeaderDto toHeader(Guia guia, int totalLineas) {
        Long origenId = guia.getSucursalOrigen() == null ? null : guia.getSucursalOrigen().getId();
        String origenNom = guia.getSucursalOrigen() == null ? null : guia.getSucursalOrigen().getNombre();
        Long sucId = guia.getSucursalDestino() == null ? null : guia.getSucursalDestino().getId();
        String sucNom = guia.getSucursalDestino() == null ? null : guia.getSucursalDestino().getNombre();
        Long ubicId = guia.getUbicacionDestino() == null ? null : guia.getUbicacionDestino().getId();
        String ubicNom = guia.getUbicacionDestino() == null ? null : guia.getUbicacionDestino().getNombre();
        return new GuiaHeaderDto(
                guia.getId(),
                guia.getNumeroGuia(),
                guia.getEstado(),
                guia.getNotas(),
                guia.getOrdenCompra(),
                origenId,
                origenNom,
                sucId,
                sucNom,
                ubicId,
                ubicNom,
                totalLineas,
                guia.getFechaCreacion());
    }

    private GuiaDetalleLineDto toDetalleLine(Guiadetalle d) {
        String paleCodigo = null;
        if (d.getPaleId() != null) {
            paleCodigo =
                    paleRepository.findById(d.getPaleId()).map(Pale::getCodigo).orElse(null);
        }
        return new GuiaDetalleLineDto(
                d.getId(),
                d.getPaleId(),
                paleCodigo,
                d.getDescripcion(),
                d.getUnidadMedida(),
                d.getCantidad(),
                d.getFechaRegistro());
    }

    private PaleEscaneadoRowDto toPaleEscaneado(Pale p) {
        return new PaleEscaneadoRowDto(
                p.getId(),
                p.getCodigo(),
                p.getEstado(),
                p.getEstadoEnvio(),
                p.getEnGuia(),
                p.getCantidadPiezas(),
                p.getOrdenesResumen());
    }

    private void markPaleEnGuia(Pale pale, boolean enGuia) {
        pale.setEnGuia(enGuia);
        paleRepository.save(pale);
    }

    private static String trimRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, field + " obligatorio");
        }
        return value.trim();
    }

    private static String trimOptional(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
