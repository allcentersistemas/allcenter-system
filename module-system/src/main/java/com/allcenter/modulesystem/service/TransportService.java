package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.PaleDtos.PaleDetailResponse;
import com.allcenter.modulesystem.dto.PaleDtos.PaleHeaderDto;
import com.allcenter.modulesystem.dto.TransportDtos.ApiMessage;
import com.allcenter.modulesystem.dto.TransportDtos.AddGuiaPaleRequest;
import com.allcenter.modulesystem.dto.TransportDtos.CreateGuiaRequest;
import com.allcenter.modulesystem.dto.TransportDtos.CreateTransporteRequest;
import com.allcenter.modulesystem.dto.TransportDtos.GuiaHeaderDto;
import com.allcenter.modulesystem.dto.TransportDtos.GuiaPaleLineDto;
import com.allcenter.modulesystem.dto.TransportDtos.GuiaResponse;
import com.allcenter.modulesystem.dto.TransportDtos.TransporteDto;
import com.allcenter.modulesystem.dto.TransportDtos.UpdateGuiaRequest;
import com.allcenter.modulesystem.dto.TransportDtos.UpdateTransporteRequest;
import com.allcenter.modulesystem.model.Guia;
import com.allcenter.modulesystem.model.GuiaPale;
import com.allcenter.modulesystem.model.Pale;
import com.allcenter.modulesystem.model.TransportAuditAction;
import com.allcenter.modulesystem.model.TransportAuditEntityTypes;
import com.allcenter.modulesystem.model.Transporte;
import com.allcenter.modulesystem.repository.GuiaPaleRepository;
import com.allcenter.modulesystem.repository.GuiaRepository;
import com.allcenter.modulesystem.repository.PaleRepository;
import com.allcenter.modulesystem.repository.TransporteRepository;
import com.allcenter.modulesystem.support.GuiaPaleCodigo;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class TransportService {

    private static final String ESTADO_BORRADOR = "BORRADOR";
    private static final String ESTADO_CONFIRMADA = "CONFIRMADA";
    private static final String ESTADO_EN_RUTA = "EN_RUTA";
    private static final String ESTADO_ENTREGADA = "ENTREGADA";
    private static final String ESTADO_CANCELADA = "CANCELADA";
    private static final String PALE_ESTADO_CERRADO = "CERRADO";

    private final TransporteRepository transporteRepository;
    private final GuiaRepository guiaRepository;
    private final GuiaPaleRepository guiaPaleRepository;
    private final PaleRepository paleRepository;
    private final TransportAuditService transportAuditService;
    private final PaleService paleService;

    public List<TransporteDto> listTransportes() {
        return transporteRepository.findAll().stream()
                .map(this::toTransporteDto)
                .toList();
    }

    public TransporteDto getTransporte(Long id) {
        Transporte transporte = transporteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Transporte no encontrado"));
        return toTransporteDto(transporte);
    }

    @Transactional
    public TransporteDto createTransporte(CreateTransporteRequest request) {
        String placa = normalizeRequired(request.placa(), "placa");
        transporteRepository.findByPlacaIgnoreCase(placa)
                .ifPresent(t -> {
                    throw new ResponseStatusException(CONFLICT, "Ya existe un transporte con esa placa");
                });

        Transporte transporte = new Transporte();
        transporte.setPlaca(placa);
        transporte.setNumeroserie(normalizeOptional(request.numeroSerie()));
        transporte.setModelo(normalizeOptional(request.modelo()));
        transporte.setMarca(normalizeOptional(request.marca()));
        transporte.setColor(normalizeOptional(request.color()));
        transporte.setDescripcion(normalizeOptional(request.descripcion()));
        transporte.setTipoVehiculo(normalizeOptional(request.tipoVehiculo()));
        transporte.setCapacidad(request.capacidad());
        transporte.setActivo(request.activo() == null ? Boolean.TRUE : request.activo());
        transporte.setFechaCreacion(LocalDateTime.now());

        transporte = transporteRepository.save(transporte);
        String tid = String.valueOf(transporte.getId());
        transportAuditService.record(
                TransportAuditAction.CREATE,
                TransportAuditEntityTypes.TRANSPORTE,
                tid,
                tid,
                "placa=" + transporte.getPlaca());
        return toTransporteDto(transporte);
    }

    @Transactional
    public TransporteDto updateTransporte(Long id, UpdateTransporteRequest request) {
        Transporte transporte = transporteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Transporte no encontrado"));

        if (request.placa() != null) {
            String placa = normalizeRequired(request.placa(), "placa");
            transporteRepository.findByPlacaIgnoreCase(placa)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(CONFLICT, "La placa ya esta registrada");
                    });
            transporte.setPlaca(placa);
        }
        if (request.numeroSerie() != null) {
            transporte.setNumeroserie(normalizeOptional(request.numeroSerie()));
        }
        if (request.modelo() != null) {
            transporte.setModelo(normalizeOptional(request.modelo()));
        }
        if (request.marca() != null) {
            transporte.setMarca(normalizeOptional(request.marca()));
        }
        if (request.color() != null) {
            transporte.setColor(normalizeOptional(request.color()));
        }
        if (request.descripcion() != null) {
            transporte.setDescripcion(normalizeOptional(request.descripcion()));
        }
        if (request.tipoVehiculo() != null) {
            transporte.setTipoVehiculo(normalizeOptional(request.tipoVehiculo()));
        }
        if (request.capacidad() != null) {
            transporte.setCapacidad(request.capacidad());
        }
        if (request.activo() != null) {
            transporte.setActivo(request.activo());
        }

        transporte = transporteRepository.save(transporte);
        String tid = String.valueOf(transporte.getId());
        transportAuditService.record(
                TransportAuditAction.UPDATE,
                TransportAuditEntityTypes.TRANSPORTE,
                tid,
                tid,
                "placa=" + transporte.getPlaca() + ";activo=" + transporte.getActivo());
        return toTransporteDto(transporte);
    }

    public List<GuiaHeaderDto> listGuias() {
        return guiaRepository.findAllWithTransporte().stream().map(this::toGuiaHeaderDto).toList();
    }

    public GuiaResponse getGuiaById(Long id) {
        Guia guia =
                guiaRepository
                        .findByIdWithTransporte(id)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Guia no encontrada"));
        return toGuiaResponse(guia);
    }

    @Transactional
    public GuiaResponse createGuia(CreateGuiaRequest request) {
        Transporte transporte =
                transporteRepository
                        .findById(request.transporteId())
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Transporte no encontrado"));
        if (Boolean.FALSE.equals(transporte.getActivo())) {
            throw new ResponseStatusException(BAD_REQUEST, "El transporte seleccionado esta inactivo");
        }

        String numeroGuia = GuiaPaleCodigo.normalizeNumeroGuia(normalizeRequired(request.numeroGuia(), "numeroGuia"));
        if (guiaRepository.existsByNumeroGuiaIgnoreCase(numeroGuia)) {
            throw new ResponseStatusException(CONFLICT, "Ya existe una guia con ese numero");
        }

        Guia guia = new Guia();
        guia.setNumeroGuia(numeroGuia);
        guia.setTransporte(transporte);
        guia.setChoferNombre(normalizeRequired(request.choferNombre(), "choferNombre"));
        guia.setChoferDocumento(normalizeOptional(request.choferDocumento()));
        guia.setEstado(ESTADO_BORRADOR);
        guia.setNotas(normalizeOptional(request.notas()));
        guia.setFechaSalida(request.fechaSalida());
        guia.setFechaEntrega(null);
        guia.setCreadoPor(request.creadoPor());
        guia.setFechaCreacion(LocalDateTime.now());
        guia = guiaRepository.save(guia);

        String cid = String.valueOf(guia.getId());
        transportAuditService.record(
                TransportAuditAction.CREATE,
                TransportAuditEntityTypes.GUIA,
                cid,
                cid,
                "numeroGuia="
                        + guia.getNumeroGuia()
                        + ";transporteId="
                        + transporte.getId()
                        + ";placa="
                        + transporte.getPlaca()
                        + ";chofer="
                        + guia.getChoferNombre()
                        + ";estado="
                        + guia.getEstado());
        return toGuiaResponse(guia);
    }

    @Transactional
    public GuiaResponse updateGuia(Long id, UpdateGuiaRequest request) {
        Guia guia =
                guiaRepository
                        .findByIdWithTransporte(id)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Guia no encontrada"));

        String estadoPrevio = guia.getEstado();

        if (request.choferNombre() != null) {
            guia.setChoferNombre(normalizeRequired(request.choferNombre(), "choferNombre"));
        }
        if (request.choferDocumento() != null) {
            guia.setChoferDocumento(normalizeOptional(request.choferDocumento()));
        }
        if (request.notas() != null) {
            guia.setNotas(normalizeOptional(request.notas()));
        }
        if (request.fechaSalida() != null) {
            guia.setFechaSalida(request.fechaSalida());
        }
        if (request.fechaEntrega() != null) {
            guia.setFechaEntrega(request.fechaEntrega());
        }
        if (request.estado() != null) {
            String normalizedEstado = normalizeEstado(request.estado());
            validateEstadoTransition(guia.getEstado(), normalizedEstado);
            guia.setEstado(normalizedEstado);
            if (ESTADO_ENTREGADA.equals(normalizedEstado) && guia.getFechaEntrega() == null) {
                guia.setFechaEntrega(LocalDateTime.now());
            }
        }
        guia = guiaRepository.save(guia);
        String cid = String.valueOf(guia.getId());
        StringBuilder detail = new StringBuilder();
        if (request.estado() != null && estadoPrevio != null && !estadoPrevio.equals(guia.getEstado())) {
            detail.append("estadoAnterior=").append(estadoPrevio).append(";estadoNuevo=").append(guia.getEstado());
        } else {
            detail.append("estado=").append(guia.getEstado());
        }
        detail.append(";numeroGuia=").append(guia.getNumeroGuia()).append(";chofer=").append(guia.getChoferNombre());
        transportAuditService.record(
                TransportAuditAction.UPDATE, TransportAuditEntityTypes.GUIA, cid, cid, detail.toString());
        return toGuiaResponse(guia);
    }

    @Transactional
    public GuiaResponse addPale(Long guiaId, AddGuiaPaleRequest request) {
        Guia guia =
                guiaRepository
                        .findByIdWithTransporte(guiaId)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Guia no encontrada"));
        validateGuiaEditableForPales(guia);

        if (guiaPaleRepository.existsByGuiaIdAndPaleId(guiaId, request.paleId())) {
            throw new ResponseStatusException(CONFLICT, "Ese pale ya fue agregado a la guia");
        }

        PaleForTransport pale = fetchPalletFromPaleModule(request.paleId());
        validatePalletReadyForTransport(pale);
        resolvePaleCodigo(request.paleCodigo(), pale.codigo());

        Pale paleEntity =
                paleRepository
                        .findById(request.paleId())
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "El pale no existe"));

        int lineIndex = Math.toIntExact(guiaPaleRepository.countByGuiaId(guiaId)) + 1;
        String codigo = GuiaPaleCodigo.build(guia.getNumeroGuia(), lineIndex);
        while (guiaPaleRepository.existsByCodigoIgnoreCase(codigo)) {
            lineIndex++;
            codigo = GuiaPaleCodigo.build(guia.getNumeroGuia(), lineIndex);
        }

        GuiaPale gp = new GuiaPale();
        gp.setGuia(guia);
        gp.setPale(paleEntity);
        gp.setCodigo(codigo);
        gp.setCantidad(request.cantidad());
        gp.setObservacion(normalizeOptional(request.observacion()));
        gp.setFechaRegistro(LocalDateTime.now());
        gp = guiaPaleRepository.save(gp);

        String corr = String.valueOf(guiaId);
        transportAuditService.record(
                TransportAuditAction.CREATE,
                TransportAuditEntityTypes.GUIA_PALE,
                String.valueOf(gp.getId()),
                corr,
                "codigo="
                        + gp.getCodigo()
                        + ";paleId="
                        + request.paleId()
                        + ";paleCodigo="
                        + pale.codigo()
                        + ";cantidad="
                        + request.cantidad());
        return toGuiaResponse(guia);
    }

    @Transactional
    public ApiMessage removePale(Long guiaId, Long guiaPaleId) {
        Guia guia =
                guiaRepository
                        .findByIdWithTransporte(guiaId)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Guia no encontrada"));
        validateGuiaEditableForPales(guia);

        GuiaPale gp =
                guiaPaleRepository
                        .findByIdWithRelations(guiaPaleId)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Linea de guia no encontrada"));
        if (!gp.getGuia().getId().equals(guiaId)) {
            throw new ResponseStatusException(BAD_REQUEST, "La linea no pertenece a la guia indicada");
        }
        String corr = String.valueOf(guiaId);
        transportAuditService.record(
                TransportAuditAction.DELETE,
                TransportAuditEntityTypes.GUIA_PALE,
                String.valueOf(guiaPaleId),
                corr,
                "codigo=" + gp.getCodigo() + ";paleId=" + gp.getPale().getId());
        guiaPaleRepository.delete(gp);
        return new ApiMessage(true, "Pale removido de la guia");
    }

    private void validateGuiaEditableForPales(Guia guia) {
        if (ESTADO_ENTREGADA.equals(guia.getEstado()) || ESTADO_CANCELADA.equals(guia.getEstado())) {
            throw new ResponseStatusException(BAD_REQUEST, "No se pueden modificar pales en una guia cerrada");
        }
    }

    private String normalizeEstado(String estado) {
        String normalized = normalizeRequired(estado, "estado").toUpperCase();
        if (!ESTADO_BORRADOR.equals(normalized)
                && !ESTADO_CONFIRMADA.equals(normalized)
                && !ESTADO_EN_RUTA.equals(normalized)
                && !ESTADO_ENTREGADA.equals(normalized)
                && !ESTADO_CANCELADA.equals(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "Estado de guia no valido");
        }
        return normalized;
    }

    private void validateEstadoTransition(String actual, String siguiente) {
        if (actual == null || actual.equals(siguiente)) {
            return;
        }
        if (ESTADO_CANCELADA.equals(actual) || ESTADO_ENTREGADA.equals(actual)) {
            throw new ResponseStatusException(BAD_REQUEST, "La guia ya esta finalizada y no acepta cambios de estado");
        }
        if (ESTADO_BORRADOR.equals(actual) && ESTADO_ENTREGADA.equals(siguiente)) {
            throw new ResponseStatusException(BAD_REQUEST, "No se puede marcar entregada sin pasar por ruta");
        }
    }

    private PaleForTransport fetchPalletFromPaleModule(Long paleEnvioId) {
        try {
            PaleDetailResponse detail = paleService.getById(paleEnvioId);
            PaleHeaderDto pallet = detail.pallet();
            if (pallet == null || pallet.codigo() == null || pallet.estado() == null) {
                throw new ResponseStatusException(BAD_REQUEST, "No se encontro informacion de pallet");
            }
            return new PaleForTransport(pallet.codigo(), pallet.estado());
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ResponseStatusException(NOT_FOUND, "El pale no existe");
            }
            throw ex;
        }
    }

    private void validatePalletReadyForTransport(PaleForTransport pallet) {
        if (!PALE_ESTADO_CERRADO.equalsIgnoreCase(pallet.estado())) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Solo se pueden cargar pales cerrados. Estado actual: " + pallet.estado());
        }
    }

    private String resolvePaleCodigo(String requestCode, String orderCode) {
        String requestNormalized = normalizeOptional(requestCode);
        if (requestNormalized == null) {
            return orderCode;
        }
        if (!requestNormalized.equalsIgnoreCase(orderCode)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "El paleCodigo no coincide con el registro en module-system");
        }
        return orderCode;
    }

    private String readRequiredText(Map<?, ?> map, String key, String errorMessage) {
        Object value = map.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, errorMessage);
        }
        return value.toString().trim();
    }

    private GuiaResponse toGuiaResponse(Guia guia) {
        List<GuiaPaleLineDto> pales =
                guiaPaleRepository.findByGuiaIdWithPale(guia.getId()).stream()
                        .map(this::toGuiaPaleLineDto)
                        .toList();
        return new GuiaResponse(toGuiaHeaderDto(guia), pales);
    }

    private GuiaHeaderDto toGuiaHeaderDto(Guia guia) {
        Long transporteId = guia.getTransporte() == null ? null : guia.getTransporte().getId();
        String placa = guia.getTransporte() == null ? null : guia.getTransporte().getPlaca();
        Integer totalPales = Math.toIntExact(guiaPaleRepository.countByGuiaId(guia.getId()));
        return new GuiaHeaderDto(
                guia.getId(),
                guia.getNumeroGuia(),
                transporteId,
                placa,
                guia.getChoferNombre(),
                guia.getChoferDocumento(),
                guia.getEstado(),
                guia.getNotas(),
                totalPales,
                guia.getFechaSalida(),
                guia.getFechaEntrega(),
                guia.getFechaCreacion());
    }

    private GuiaPaleLineDto toGuiaPaleLineDto(GuiaPale gp) {
        String paleCodigo = gp.getPale() == null ? null : gp.getPale().getCodigo();
        Long paleId = gp.getPale() == null ? null : gp.getPale().getId();
        return new GuiaPaleLineDto(
                gp.getId(),
                gp.getCodigo(),
                paleId,
                paleCodigo,
                gp.getCantidad(),
                gp.getObservacion(),
                gp.getFechaRegistro());
    }

    private TransporteDto toTransporteDto(Transporte transporte) {
        return new TransporteDto(
                transporte.getId(),
                transporte.getPlaca(),
                transporte.getNumeroserie(),
                transporte.getModelo(),
                transporte.getMarca(),
                transporte.getColor(),
                transporte.getDescripcion(),
                transporte.getTipoVehiculo(),
                transporte.getCapacidad(),
                transporte.getActivo(),
                transporte.getFechaCreacion());
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new ResponseStatusException(BAD_REQUEST, "El campo " + fieldName + " es obligatorio");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record PaleForTransport(String codigo, String estado) {}
}
