package com.allcenter.modulepale.service;

import com.allcenter.modulelocation.model.Sucursal;
import com.allcenter.modulelocation.model.Ubicacion;
import com.allcenter.modulelocation.repository.SucursalRepository;
import com.allcenter.modulelocation.repository.UbicacionRepository;
import com.allcenter.modulepale.dto.PaleDtos.ApiMessage;
import com.allcenter.modulepale.dto.PaleDtos.CatalogDto;
import com.allcenter.modulepale.dto.PaleDtos.ClosePaleRequest;
import com.allcenter.modulepale.dto.PaleDtos.CreatePaleRequest;
import com.allcenter.modulepale.dto.PaleDtos.CreateSucursalRequest;
import com.allcenter.modulepale.dto.PaleDtos.CreateUbicacionRequest;
import com.allcenter.modulepale.dto.PaleDtos.PaleDetailItemDto;
import com.allcenter.modulepale.dto.PaleDtos.PaleDetailResponse;
import com.allcenter.modulepale.dto.PaleDtos.PaleAuditEntryDto;
import com.allcenter.modulepale.dto.PaleDtos.PaleHeaderDto;
import com.allcenter.modulepale.dto.PaleDtos.ScanPieceToPaleRequest;
import com.allcenter.modulepale.dto.PaleDtos.SucursalDto;
import com.allcenter.modulepale.dto.PaleDtos.UbicacionDto;
import com.allcenter.modulepale.dto.PaleDtos.UpdatePaleRequest;
import com.allcenter.modulepale.model.Pale;
import com.allcenter.modulepale.model.PaleAuditEntry;
import com.allcenter.modulepale.model.PaleDetalle;
import com.allcenter.modulepale.support.PaleAuditSourceCapture;
import com.allcenter.modulepale.repository.PaleAuditEntryRepository;
import com.allcenter.modulepale.repository.PaleDetalleRepository;
import com.allcenter.modulepale.repository.PaleRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaleService {

    private final PaleRepository paleRepository;
    private final PaleDetalleRepository detalleRepository;
    private final PaleAuditEntryRepository auditEntryRepository;
    private final SucursalRepository sucursalRepository;
    private final UbicacionRepository ubicacionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.biesse.base-url:http://localhost:8086}")
    private String biesseBaseUrl;



    public List<PaleHeaderDto> listPallets() {
        return paleRepository.findAllWithRelations().stream()
                .sorted((a, b) -> b.getFechaCreacion().compareTo(a.getFechaCreacion()))
                .map(this::toHeader)
                .toList();
    }

    public List<PaleAuditEntryDto> listAudit(Long paleId, String action, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        String cleanAction = action == null || action.isBlank() ? null : action.trim();
        PageRequest page = PageRequest.of(0, safeLimit);
        List<PaleAuditEntry> entries;
        if (paleId != null && cleanAction != null) {
            entries = auditEntryRepository.findByPaleIdAndActionIgnoreCaseOrderByOccurredAtDesc(paleId, cleanAction, page);
        } else if (paleId != null) {
            entries = auditEntryRepository.findByPaleIdOrderByOccurredAtDesc(paleId, page);
        } else if (cleanAction != null) {
            entries = auditEntryRepository.findByActionIgnoreCaseOrderByOccurredAtDesc(cleanAction, page);
        } else {
            entries = auditEntryRepository.findAllByOrderByOccurredAtDesc(page);
        }
        return entries.stream()
                .map(this::toAuditDto)
                .toList();
    }

    public CatalogDto getCatalogs() {
        return new CatalogDto(getBranches(), getLocations());
    }

    public List<SucursalDto> getBranches() {
        return sucursalRepository.findAll().stream()
                .map(
                        s ->
                                new SucursalDto(
                                        s.getId(),
                                        s.getNombre(),
                                        s.getDireccion(),
                                        s.getCiudad(),
                                        s.getDepartamento()))
                .toList();
    }

    public List<UbicacionDto> getLocations() {
        return ubicacionRepository.findAll().stream()
                .map(
                        u ->
                                new UbicacionDto(
                                        u.getId(),
                                        u.getNombre(),
                                        u.getDireccion(),
                                        u.getDistrito(),
                                        u.getDepartamento(),
                                        u.getCiudad()))
                .toList();
    }

    @Transactional
    public SucursalDto createBranch(CreateSucursalRequest req) {
        Sucursal s = new Sucursal();
        s.setNombre(req.nombre().trim());
        s.setDireccion(req.direccion());
        s.setCiudad(req.ciudad());
        s.setDepartamento(req.departamento());
        s = sucursalRepository.save(s);
        return new SucursalDto(s.getId(), s.getNombre(), s.getDireccion(), s.getCiudad(), s.getDepartamento());
    }

    @Transactional
    public UbicacionDto createLocation(CreateUbicacionRequest req) {
        Ubicacion u = new Ubicacion();
        u.setNombre(req.nombre().trim());
        u.setDireccion(req.direccion());
        u.setDistrito(req.distrito());
        u.setDepartamento(req.departamento());
        u.setCiudad(req.ciudad());
        u = ubicacionRepository.save(u);
        return new UbicacionDto(
                u.getId(), u.getNombre(), u.getDireccion(), u.getDistrito(), u.getDepartamento(), u.getCiudad());
    }

    @Transactional
    public PaleDetailResponse createPale(CreatePaleRequest req) {
        Sucursal origin = sucursalRepository.findById(req.branchId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Sucursal origen no encontrada"));
        Ubicacion originLocation = null;
        Sucursal destination = null;
        Ubicacion destinationLocation = null;
        if (req.destinationLocationId() != null) {
            destinationLocation =
                    ubicacionRepository
                            .findById(req.destinationLocationId())
                            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ubicacion destino no encontrada"));
            if (req.destinationBranchId() != null) {
                destination =
                        sucursalRepository
                                .findById(req.destinationBranchId())
                                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Sucursal destino no encontrada"));
            }
        } else {
            if (req.destinationBranchId() == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Indique sucursal destino u obra (ubicacion) destino");
            }
            destination =
                    sucursalRepository
                            .findById(req.destinationBranchId())
                            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Sucursal destino no encontrada"));
        }

        String code = nextPaleCode();
        paleRepository.findByCodigoIgnoreCase(code).ifPresent(p ->
                { throw new ResponseStatusException(CONFLICT, "Ya existe un pale con ese codigo"); });

        Pale pale = new Pale();
        pale.setCodigo(code);
        pale.setSucursalOrigen(origin);
        pale.setSucursalDestino(destination);
        pale.setUbicacionOrigen(originLocation);
        pale.setUbicacionDestino(destinationLocation);
        pale.setEstado("ABIERTO");
        pale.setCantidadPiezas(0);
        pale.setCantidadOrdenes(0);
        pale.setOrdenesResumen("");
        pale.setNotas(req.notes());
        pale.setCreadoPor(req.createdBy());
        pale.setFechaCreacion(LocalDateTime.now());
        pale = paleRepository.save(pale);
        recordAudit("CREATE", "Pale", String.valueOf(pale.getId()), pale, "Pale creado");
        return toDetailResponse(pale);
    }

    public PaleDetailResponse getByCode(String code) {
        Pale pale = paleRepository.findByCodigoIgnoreCaseWithRelations(code)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Pale no encontrado"));
        return toDetailResponse(pale);
    }

    public PaleDetailResponse getById(Long id) {
        Pale pale = paleRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Pale no encontrado"));
        return toDetailResponse(pale);
    }

    @Transactional
    public PaleDetailResponse updatePale(Long id, UpdatePaleRequest req) {
        Pale pale = paleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Pale no encontrado"));
        if (req == null) {
            req = new UpdatePaleRequest(null, null, null, null, null, null, null);
        }
        if (req.code() != null) {
            String code = normalizeRequired(req.code(), "codigo");
            paleRepository.findByCodigoIgnoreCase(code)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(CONFLICT, "Ya existe un pale con ese codigo");
                    });
            pale.setCodigo(code);
        }
        if (req.estado() != null) {
            String estado = normalizeRequired(req.estado(), "estado").toUpperCase();
            pale.setEstado(estado);
            if ("CERRADO".equals(estado) && pale.getFechaCierre() == null) {
                pale.setFechaCierre(LocalDateTime.now());
            }
            if (!"CERRADO".equals(estado)) {
                pale.setFechaCierre(null);
            }
        }
        if (req.branchId() != null) {
            Sucursal origin = sucursalRepository.findById(req.branchId())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Sucursal origen no encontrada"));
            pale.setSucursalOrigen(origin);
        }
        if (req.originLocationId() != null) {
            Ubicacion originLocation = ubicacionRepository.findById(req.originLocationId())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ubicacion origen no encontrada"));
            pale.setUbicacionOrigen(originLocation);
        }
        if (req.destinationBranchId() != null) {
            Sucursal destination = sucursalRepository.findById(req.destinationBranchId())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Sucursal destino no encontrada"));
            pale.setSucursalDestino(destination);
        }
        if (req.destinationLocationId() != null) {
            Ubicacion destinationLocation = ubicacionRepository.findById(req.destinationLocationId())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ubicacion destino no encontrada"));
            pale.setUbicacionDestino(destinationLocation);
        }
        if (req.notes() != null) {
            pale.setNotas(req.notes().trim());
        }
        paleRepository.save(pale);
        recordAudit("UPDATE", "Pale", String.valueOf(pale.getId()), pale, "Informacion del pale actualizada");
        Pale fresh = paleRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Pale no encontrado"));
        return toDetailResponse(fresh);
    }

    @Transactional
    public PaleDetailResponse removeDetail(Long paleId, Long detailId) {
        Pale pale = paleRepository.findById(paleId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Pale no encontrado"));
        PaleDetalle detail = detalleRepository.findById(detailId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Detalle no encontrado"));
        if (!detail.getPale().getId().equals(paleId)) {
            throw new ResponseStatusException(BAD_REQUEST, "El detalle no pertenece al pale indicado");
        }
        detalleRepository.delete(detail);
        refreshPaleSummary(pale);
        recordAudit(
                "DELETE_DETAIL",
                "PaleDetalle",
                String.valueOf(detailId),
                pale,
                "Detalle eliminado. piezaId=" + detail.getPiezaId() + ", partId=" + detail.getPartId());
        Pale fresh = paleRepository.findByIdWithRelations(paleId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Pale no encontrado"));
        return toDetailResponse(fresh);
    }

    @Transactional
    public ApiMessage scanPiece(String authorization, Long paleId, ScanPieceToPaleRequest req) {
        Pale pale = paleRepository.findById(paleId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Pale no encontrado"));
        if (!"ABIERTO".equalsIgnoreCase(pale.getEstado())) {
            throw new ResponseStatusException(BAD_REQUEST, "El pale ya esta cerrado");
        }
        detalleRepository.findByPale_IdAndPiezaId(paleId, req.pieceId())
                .ifPresent(d -> {
                    throw new ResponseStatusException(CONFLICT, "La pieza ya fue agregada al pale");
                });

        Map<String, Object> pieceData = fetchPieceDataFromBiesse(authorization, req.pieceId());
        if (pieceData == null) {
            throw new ResponseStatusException(NOT_FOUND, "Pieza no encontrada");
        }

        PaleDetalle detail = new PaleDetalle();
        detail.setPale(pale);
        detail.setPiezaId(req.pieceId());
        detail.setPartId(((Number) pieceData.get("partid")).longValue());
        detail.setOrderId(((Number) pieceData.get("orderid")).longValue());
        detail.setOrderName((String) pieceData.get("ordername"));
        detail.setPartCode((String) pieceData.get("partcode"));
        Object number = pieceData.get("numero_pieza");
        if (number == null) {
            number = pieceData.get("numeroPieza");
        }
        if (number == null) {
            number = pieceData.get("numeropieza");
        }
        detail.setNumeroPieza(number == null ? null : ((Number) number).intValue());
        detail.setDescripcion(
                firstString(pieceData, "part_descripcion", "partDescripcion", "descripcion"));
        detail.setDescripcion1(
                firstString(pieceData, "part_descripcion1", "partDescripcion1", "descripcion1"));
        detail.setTotalPiezas(firstInteger(pieceData, "cantidad_parte", "cantidadParte", "cantidad"));
        {
            String medidaText = firstString(pieceData, "medida");
            if (medidaText != null && !medidaText.isBlank()) {
                detail.setMedida(medidaText.trim());
            } else {
                detail.setMedida(
                        formatMedidaPair(
                                firstDouble(
                                        pieceData,
                                        "longitud_parte",
                                        "longitud",
                                        "longitudParte",
                                        "l"),
                                firstDouble(
                                        pieceData,
                                        "ancho_parte",
                                        "ancho",
                                        "anchoParte",
                                        "w")));
            }
            if (detail.getMedida() == null || detail.getMedida().isBlank()) {
                String fromLocalPartes = tryLoadMedidaFromPartes(detail.getPartId());
                if (fromLocalPartes != null && !fromLocalPartes.isBlank()) {
                    detail.setMedida(fromLocalPartes);
                }
            }
        }
        detail.setAgregadoPor(req.addedBy());
        detail.setFechaAgregado(LocalDateTime.now());
        detalleRepository.save(detail);

        registerPieceScanInBiesse(authorization, req.pieceId(), pale.getCodigo());

        refreshPaleSummary(pale);
        recordAudit(
                "SCAN_PIECE",
                "PaleDetalle",
                String.valueOf(detail.getId()),
                pale,
                "Pieza agregada al pale. piezaId=" + req.pieceId());
        return new ApiMessage(true, "Pieza agregada al pale " + pale.getCodigo());
    }

    @Transactional
    public ApiMessage closePale(Long paleId, ClosePaleRequest req) {
        Pale pale = paleRepository.findById(paleId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Pale no encontrado"));
        if (!"ABIERTO".equalsIgnoreCase(pale.getEstado())) {
            return new ApiMessage(true, "El pale ya estaba cerrado");
        }
        pale.setEstado("CERRADO");
        pale.setFechaCierre(LocalDateTime.now());
        if (req != null && req.notes() != null && !req.notes().isBlank()) {
            pale.setNotas(req.notes());
        }
        paleRepository.save(pale);
        recordAudit("CLOSE", "Pale", String.valueOf(pale.getId()), pale, "Pale cerrado");
        return new ApiMessage(true, "Pale cerrado correctamente");
    }

    private void refreshPaleSummary(Pale pale) {
        List<PaleDetalle> details = detalleRepository.findByPale_IdOrderByFechaAgregadoDesc(pale.getId());
        pale.setCantidadPiezas(details.size());
        Set<String> orders = new LinkedHashSet<>();
        for (PaleDetalle d : details) {
            String label = d.getOrderName() == null ? String.valueOf(d.getOrderId()) : d.getOrderName();

            orders.add(label);
        }
        pale.setCantidadOrdenes(orders.size());
        pale.setOrdenesResumen(String.join(", ", orders));
        paleRepository.save(pale);
    }

    /**
     * Marca la pieza como escaneada en module-biesse (tabla piezas). Si falla, la transacción revierte el detalle del
     * palé.
     */
    private void registerPieceScanInBiesse(String authorization, Long pieceId, String paleCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorization);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put("pieceId", pieceId);
        body.put("observations", "Agregada a pale " + paleCode);
        body.put("equipment", "PALLET");
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(
                            biesseBaseUrl + "/api/biesse/scan/pieces/scan", entity, Map.class);
            Map<?, ?> resp = response.getBody();
            if (resp != null && Boolean.FALSE.equals(resp.get("success"))) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        resp.get("message") != null ? resp.get("message").toString() : "No se pudo marcar pieza escaneada");
            }
        } catch (org.springframework.web.client.HttpStatusCodeException ex) {
            String msg = "No se pudo registrar escaneo de pieza en module-biesse";
            if (ex.getStatusCode().value() == 400) {
                msg = "Pieza no escaneable (puede estar ya escaneada o no existir)";
            }
            throw new ResponseStatusException(ex.getStatusCode(), msg);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Fallo de comunicacion con module-biesse al escanear pieza");
        }
    }

    private Map<String, Object> fetchPieceDataFromBiesse(String authorization, Long pieceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorization);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map> response =
                    restTemplate.exchange(
                            biesseBaseUrl + "/api/biesse/scan/pieces/" + pieceId,
                            HttpMethod.GET,
                            entity,
                            Map.class);
            return response.getBody();
        } catch (org.springframework.web.client.HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                return null;
            }
            throw new ResponseStatusException(BAD_REQUEST, "No se pudo consultar pieza en module-biesse");
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Fallo de comunicacion con module-biesse");
        }
    }

    private PaleDetailResponse toDetailResponse(Pale pale) {
        List<PaleDetalle> rows =
                detalleRepository.findByPale_IdOrderByFechaAgregadoDesc(pale.getId());
        Set<Long> piezaIds =
                rows.stream().map(PaleDetalle::getPiezaId).collect(Collectors.toSet());
        Map<Long, LineaPiezaEnrichment> enrich = loadPiezaParteEnrichment(piezaIds);
        List<PaleDetailItemDto> details =
                rows.stream()
                        .map(
                                d -> {
                                    LineaPiezaEnrichment e = enrich.get(d.getPiezaId());
                                    String partDesc =
                                            d.getDescripcion() != null
                                                    ? d.getDescripcion()
                                                    : (e != null ? e.partDescripcion() : null);
                                    String partDesc1 =
                                            d.getDescripcion1() != null
                                                    ? d.getDescripcion1()
                                                    : (e != null ? e.partDescripcion1() : null);
                                    Integer planPart =
                                            d.getTotalPiezas() != null
                                                    ? d.getTotalPiezas()
                                                    : (e != null ? e.piezasPlanParte() : null);
                                    String medida =
                                            d.getMedida() != null && !d.getMedida().isBlank()
                                                    ? d.getMedida()
                                                    : (e != null ? e.medida() : null);
                                    return new PaleDetailItemDto(
                                            d.getId(),
                                            d.getPiezaId(),
                                            d.getPartId(),
                                            d.getOrderId(),
                                            d.getOrderName(),
                                            d.getPartCode(),
                                            d.getNumeroPieza(),
                                            d.getFechaAgregado(),
                                            partDesc,
                                            partDesc1,
                                            planPart,
                                            medida);
                                })
                        .toList();
        return new PaleDetailResponse(toHeader(pale), details);
    }

    private record LineaPiezaEnrichment(
            String partDescripcion, String partDescripcion1, Integer piezasPlanParte, String medida) {}

    /**
     * Descripciones de parte ({@code partes.descripcion}, {@code partes.descripcion1}) y cantidad programada,
     * alineado con {@code proyecto_final/servicio_sincronizacion/database/database_schema.py}.
     */
    private Map<Long, LineaPiezaEnrichment> loadPiezaParteEnrichment(Set<Long> piezaIds) {
        if (piezaIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = piezaIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql =
                """
                SELECT z.piezaid, p.descripcion AS part_descripcion, p.descripcion1 AS part_descripcion1,
                       p.cantidad AS cantidad_parte, p.longitud AS longitud_parte, p.ancho AS ancho_parte
                FROM piezas z
                JOIN partes p ON p.partid = z.partid
                WHERE z.piezaid IN (%s)
                """
                        .formatted(placeholders);
        try {
            Object[] args = piezaIds.toArray();
            List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, args);
            Map<Long, LineaPiezaEnrichment> out = new HashMap<>();
            for (Map<String, Object> row : list) {
                Number pid = (Number) row.get("piezaid");
                if (pid == null) {
                    continue;
                }
                Number cant = (Number) row.get("cantidad_parte");
                Integer plan = cant == null ? null : cant.intValue();
                Object pd = row.get("part_descripcion");
                Object pd1 = row.get("part_descripcion1");
                Double lon = toDoubleObj(row.get("longitud_parte"));
                Double ancho = toDoubleObj(row.get("ancho_parte"));
                String medidaFmt = formatMedidaPair(lon, ancho);
                out.put(
                        pid.longValue(),
                        new LineaPiezaEnrichment(
                                pd == null ? null : pd.toString(),
                                pd1 == null ? null : pd1.toString(),
                                plan,
                                medidaFmt));
            }
            return out;
        } catch (Exception ex) {
            log.warn("Enriquecimiento pale (partes.descripcion / cantidad) no disponible: {}", ex.getMessage());
            return Map.of();
        }
    }

    private PaleHeaderDto toHeader(Pale p) {
        Long originBranchId = p.getSucursalOrigen() == null ? null : p.getSucursalOrigen().getId();
        Long destinationBranchId = p.getSucursalDestino() == null ? null : p.getSucursalDestino().getId();
        Long ubicOrigenId = p.getUbicacionOrigen() == null ? null : p.getUbicacionOrigen().getId();
        Long ubicDestId = p.getUbicacionDestino() == null ? null : p.getUbicacionDestino().getId();
        String ubicDestNombre = p.getUbicacionDestino() == null ? null : p.getUbicacionDestino().getNombre();
        return new PaleHeaderDto(
                p.getId(),
                p.getCodigo(),
                p.getEstado(),
                p.getCantidadPiezas(),
                p.getCantidadOrdenes(),
                p.getOrdenesResumen(),
                p.getNotas(),
                originBranchId,
                p.getSucursalOrigen() == null ? null : p.getSucursalOrigen().getNombre(),
                destinationBranchId,
                p.getSucursalDestino() == null ? null : p.getSucursalDestino().getNombre(),
                ubicOrigenId,
                ubicDestId,
                ubicDestNombre,
                p.getFechaCreacion(),
                p.getFechaCierre());
    }

    private void recordAudit(String action, String entityType, String entityId, Pale pale, String details) {
        PaleAuditEntry e = new PaleAuditEntry();
        e.setOccurredAt(LocalDateTime.now());
        e.setAction(action);
        e.setEntityType(entityType);
        e.setEntityId(entityId);
        e.setPaleId(pale == null ? null : pale.getId());
        e.setPaleCodigo(pale == null ? null : pale.getCodigo());
        e.setDetails(details);
        PaleAuditSourceCapture.Captured cap = PaleAuditSourceCapture.fromCurrentRequest();
        e.setActorEmployeeId(cap.actorEmployeeId());
        e.setActorEmail(cap.actorEmail());
        e.setSourceIp(cap.sourceIp());
        e.setUserAgent(cap.userAgent());
        auditEntryRepository.save(e);
    }

    private PaleAuditEntryDto toAuditDto(PaleAuditEntry e) {
        return new PaleAuditEntryDto(
                e.getId(),
                e.getOccurredAt(),
                e.getAction(),
                e.getEntityType(),
                e.getEntityId(),
                e.getPaleId(),
                e.getPaleCodigo(),
                e.getDetails(),
                e.getActorEmployeeId(),
                e.getActorEmail(),
                e.getSourceIp(),
                e.getUserAgent());
    }

    private String nextPaleCode() {
        Long max = paleRepository.findMaxNumericCode();
        long next = (max == null ? 0L : max) + 1L;
        return String.format("%010d", next);
    }

    private static String firstString(Map<?, ?> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String k : keys) {
            Object v = getMapValue(map, k);
            if (v != null && !v.toString().isBlank()) {
                return v.toString();
            }
        }
        return null;
    }

    /** Lectura tolerante: clave exacta o misma clave sin distinguir mayúsculas (p. ej. JSON distinto). */
    private static Object getMapValue(Map<?, ?> map, String key) {
        if (map.containsKey(key)) {
            return map.get(key);
        }
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null && key.equalsIgnoreCase(e.getKey().toString())) {
                return e.getValue();
            }
        }
        return null;
    }

    private static Integer firstInteger(Map<?, ?> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String k : keys) {
            Object v = getMapValue(map, k);
            if (v instanceof Number n) {
                return n.intValue();
            }
            if (v instanceof String s && !s.isBlank()) {
                try {
                    return Integer.parseInt(s.trim());
                } catch (NumberFormatException ignored) {
                    // siguiente clave
                }
            }
        }
        return null;
    }

    private static Double firstDouble(Map<?, ?> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String k : keys) {
            Object v = getMapValue(map, k);
            Double d = toDoubleObj(v);
            if (d != null) {
                return d;
            }
        }
        return null;
    }

    private static Double toDoubleObj(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            String t = s.trim().replace(',', '.');
            if (t.isEmpty()) {
                return null;
            }
            try {
                return Double.parseDouble(t);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Si la API de Biesse no devuelve medida pero esta app comparte {@code partes} en la misma BD, rellena desde
     * {@code partes.longitud}/{@code ancho}.
     */
    private String tryLoadMedidaFromPartes(Long partId) {
        if (partId == null) {
            return null;
        }
        try {
            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(
                            "SELECT longitud, ancho FROM partes WHERE partid = ? LIMIT 1", partId);
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> row = rows.getFirst();
            Object lon = null;
            Object ancho = null;
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                String k = e.getKey();
                if ("longitud".equalsIgnoreCase(k)) {
                    lon = e.getValue();
                } else if ("ancho".equalsIgnoreCase(k)) {
                    ancho = e.getValue();
                }
            }
            return formatMedidaPair(toDoubleObj(lon), toDoubleObj(ancho));
        } catch (Exception ex) {
            log.debug("Medida desde partes local no disponible (partid={}): {}", partId, ex.getMessage());
            return null;
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "El campo " + fieldName + " es obligatorio");
        }
        return value.trim();
    }

    /** Formato compacto para impresión (mismas unidades que en {@code partes}). */
    private static String formatMedidaPair(Double longitud, Double ancho) {
        if (longitud == null && ancho == null) {
            return null;
        }
        String l = longitud == null ? "—" : trimDecimal(longitud);
        String a = ancho == null ? "—" : trimDecimal(ancho);
        return l + " × " + a;
    }

    private static String trimDecimal(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "—";
        }
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }
}
