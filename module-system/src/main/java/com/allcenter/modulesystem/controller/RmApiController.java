package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.RmApiModels;
import com.allcenter.modulesystem.dto.RmPayloadModels;
import com.allcenter.modulesystem.model.RmActaConformidad;
import com.allcenter.modulesystem.model.RmRegistroEntrada;
import com.allcenter.modulesystem.model.RmRegistroEntradaDetalle;
import com.allcenter.modulesystem.model.RmRegistroSalida;
import com.allcenter.modulesystem.model.RmRegistroSalidaDetalle;
import com.allcenter.modulesystem.model.RmRegistroVehiculo;
import com.allcenter.modulesystem.service.RmRegistroApplicationService;
import com.allcenter.modulesystem.service.RmRegistroDocumentResolver;
import com.allcenter.modulesystem.support.AuthenticatedEmployeeResolver;
import com.allcenter.modulesystem.support.PhotoFilenameCodec;
import com.allcenter.modulesystem.support.RmMediaKinds;
import com.allcenter.modulesystem.support.RmStorageService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/rm")
@RequiredArgsConstructor
public class RmApiController {

    private final RmRegistroApplicationService registroService;
    private final RmRegistroDocumentResolver documentResolver;
    private final AuthenticatedEmployeeResolver employeeResolver;
    private final PhotoFilenameCodec photoFilenameCodec;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/registros-vehiculo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@portalAuth.canCreate()")
    public RmApiModels.Created postVehiculo(MultipartHttpServletRequest request) throws IOException {
        MultipartFile data = requireDataPart(request);
        List<MultipartFile> photos = request.getFiles("photos");
        String user = trimHeaderEmail(request);
        return registroService.createRegistroVehiculo(data.getBytes(), photos, user);
    }

    @GetMapping("/registros-vehiculo")
    @PreAuthorize("@portalAuth.canRead()")
    public Page<RmApiModels.VehiculoListRow> listVehiculos(
            @RequestParam(required = false) String q, @PageableDefault(size = 20) Pageable pageable) {
        return registroService.pageVehiculos(pageable, q).map(this::toVehiculoListRow);
    }

    @GetMapping("/registros-vehiculo/{id}")
    @PreAuthorize("@portalAuth.canRead()")
    public RmApiModels.RegistroVehiculoResponse getVehiculo(@PathVariable long id) {
        RmRegistroVehiculo v = registroService.getVehiculo(id);
        List<RmApiModels.EntradaListRow> entradas =
                registroService.listEntradasByVehiculo(id).stream().map(this::toEntradaListRow).toList();
        return toVehiculoResponse(v, entradas);
    }

    @GetMapping("/registros-vehiculo/{id}/entradas")
    @PreAuthorize("@portalAuth.canRead()")
    public List<RmApiModels.EntradaListRow> listEntradasByVehiculo(@PathVariable long id) {
        return registroService.listEntradasByVehiculo(id).stream().map(this::toEntradaListRow).toList();
    }

    @PostMapping(value = "/registros-entrada", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@portalAuth.canCreate()")
    public RmApiModels.Created postEntrada(MultipartHttpServletRequest request) throws IOException {
        MultipartFile data = requireDataPart(request);
        List<MultipartFile> photos = request.getFiles("photos");
        String user = trimHeaderEmail(request);
        return registroService.createRegistroEntrada(data.getBytes(), photos, user);
    }

    /** Vehículo en borrador + documento (OC y guía) en una sola transacción. */
    @PostMapping(value = "/registros-ingreso-completo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@portalAuth.canCreate()")
    public RmApiModels.Created postIngresoCompleto(MultipartHttpServletRequest request) throws IOException {
        MultipartFile data = requireDataPart(request);
        List<MultipartFile> photos = request.getFiles("photos");
        String user = trimHeaderEmail(request);
        Long branchId =
                employeeResolver.resolve(request).map(AuthenticatedEmployeeResolver.Context::branchId).orElse(null);
        return registroService.createIngresoCompleto(data.getBytes(), photos, user, branchId);
    }

    /** Vehículo en borrador + salida en una sola transacción (Android). */
    @PostMapping(value = "/registros-salida-completo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@portalAuth.canCreate()")
    public RmApiModels.Created postSalidaCompleto(MultipartHttpServletRequest request) throws IOException {
        MultipartFile data = requireDataPart(request);
        List<MultipartFile> photos = request.getFiles("photos");
        String user = trimHeaderEmail(request);
        Long branchId =
                employeeResolver.resolve(request).map(AuthenticatedEmployeeResolver.Context::branchId).orElse(null);
        return registroService.createSalidaCompleto(data.getBytes(), photos, user, branchId);
    }

    @GetMapping("/registros-entrada")
    @Transactional(readOnly = true)
    @PreAuthorize("@portalAuth.canRead()")
    public Page<RmApiModels.EntradaListRow> listEntradas(
            @RequestParam(required = false) String q, @PageableDefault(size = 20) Pageable pageable) {
        return registroService.pageEntradas(pageable, q).map(this::toEntradaListRow);
    }

    @GetMapping("/registros-entrada/{id}")
    @PreAuthorize("@portalAuth.canRead()")
    public RmApiModels.RegistroEntradaResponse getEntrada(@PathVariable long id) {
        return toEntradaResponse(registroService.getEntrada(id));
    }

    @PostMapping("/registros-entrada/{id}/cancelar")
    @PreAuthorize("@portalAuth.canCancel()")
    public RmApiModels.Created cancelEntrada(
            @PathVariable long id,
            @RequestBody RmPayloadModels.CancelPayload body,
            HttpServletRequest request) {
        registroService.cancelRegistroEntrada(id, body, trimHeaderEmail(request));
        return new RmApiModels.Created(id);
    }

    @PostMapping(value = "/registros-salida", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@portalAuth.canCreate()")
    public RmApiModels.Created postSalida(MultipartHttpServletRequest request) throws IOException {
        MultipartFile data = requireDataPart(request);
        List<MultipartFile> photos = request.getFiles("photos");
        String user = trimHeaderEmail(request);
        Long branchId =
                employeeResolver.resolve(request).map(AuthenticatedEmployeeResolver.Context::branchId).orElse(null);
        return registroService.createRegistroSalida(data.getBytes(), photos, user, branchId);
    }

    @GetMapping("/registros-salida")
    @Transactional(readOnly = true)
    @PreAuthorize("@portalAuth.canRead()")
    public Page<RmApiModels.SalidaListRow> listSalidas(
            @RequestParam(required = false) String q, @PageableDefault(size = 20) Pageable pageable) {
        return registroService.pageSalidas(pageable, q).map(this::toSalidaListRow);
    }

    @GetMapping("/registros-salida/{id}")
    @PreAuthorize("@portalAuth.canRead()")
    public RmApiModels.RegistroSalidaResponse getSalida(@PathVariable long id) {
        return toSalidaResponse(registroService.getSalida(id));
    }

    @PostMapping("/registros-salida/{id}/cancelar")
    @PreAuthorize("@portalAuth.canCancel()")
    public RmApiModels.Created cancelSalida(
            @PathVariable long id,
            @RequestBody RmPayloadModels.CancelPayload body,
            HttpServletRequest request) {
        registroService.cancelRegistroSalida(id, body, trimHeaderEmail(request));
        return new RmApiModels.Created(id);
    }

    @PostMapping(value = "/actas-conformidad", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@portalAuth.canCreate()")
    public RmApiModels.Created postActa(MultipartHttpServletRequest request) throws IOException {
        MultipartFile data = requireDataPart(request);
        List<MultipartFile> photos = request.getFiles("photos");
        String user = trimHeaderEmail(request);
        return registroService.createActaConformidad(data.getBytes(), photos, user);
    }

    @GetMapping("/actas-conformidad")
    @PreAuthorize("@portalAuth.canRead()")
    public Page<RmApiModels.ActaListRow> listActas(@PageableDefault(size = 20) Pageable pageable) {
        return registroService
                .pageActas(pageable)
                .map(a -> new RmApiModels.ActaListRow(
                        a.getId(),
                        a.getRazonSocialNombre(),
                        a.getDecision(),
                        resolveActaEstado(a.getEstado()),
                        a.getMotivoCancelacion(),
                        a.getCanceladoAt(),
                        a.getCanceladoPorEmail(),
                        a.getCanceladoPorNombre(),
                        a.getCreatedAt()));
    }

    @GetMapping("/actas-conformidad/{id}")
    @PreAuthorize("@portalAuth.canRead()")
    public RmApiModels.ActaConformidadResponse getActa(@PathVariable long id) {
        return toActaResponse(registroService.getActa(id));
    }

    @PostMapping("/actas-conformidad/{id}/cancelar")
    @PreAuthorize("@portalAuth.canCancel()")
    public RmApiModels.Created cancelActa(
            @PathVariable long id,
            @RequestBody RmPayloadModels.CancelPayload body,
            HttpServletRequest request) {
        registroService.cancelActaConformidad(id, body, trimHeaderEmail(request));
        return new RmApiModels.Created(id);
    }

    @GetMapping("/media/{kind}/{recordId}/{filename:.+}")
    @PreAuthorize("@portalAuth.canRead()")
    public ResponseEntity<Resource> getMedia(
            @PathVariable String kind,
            @PathVariable long recordId,
            @PathVariable String filename) {
        Resource body = registroService.loadMediaFile(kind, recordId, filename);
        MediaType contentType = probeMediaType(filename);
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(body);
    }

    private record DocumentFields(String ocNumero, String numeroGuia) {}

    private DocumentFields documentFields(RmRegistroEntrada e) {
        RmRegistroDocumentResolver.Resolved doc = documentResolver.forEntrada(e);
        return new DocumentFields(trimNullable(doc.ocNumero()), trimNullable(doc.numeroGuia()));
    }

    private DocumentFields documentFields(RmRegistroSalida s) {
        RmRegistroDocumentResolver.Resolved doc = documentResolver.forSalida(s);
        return new DocumentFields(trimNullable(doc.ocNumero()), trimNullable(doc.numeroGuia()));
    }

    private RmApiModels.EntradaListRow toEntradaListRow(RmRegistroEntrada e) {
        Long vehiculoId = e.getRegistroVehiculo() == null ? null : e.getRegistroVehiculo().getId();
        DocumentFields doc = documentFields(e);
        return new RmApiModels.EntradaListRow(
                e.getId(),
                e.getNumeroregistro(),
                vehiculoId,
                e.getFecha(),
                e.getHora(),
                e.getTipoDocumento(),
                doc.ocNumero(),
                doc.numeroGuia(),
                e.getGuiaInventarioId(),
                e.getRecepcionEstado(),
                e.getMotivoCancelacion(),
                e.getCanceladoAt(),
                e.getCanceladoPorEmail(),
                e.getCanceladoPorNombre(),
                e.getCreatedAt(),
                e.getLineas());
    }

    private RmApiModels.SalidaListRow toSalidaListRow(RmRegistroSalida s) {
        DocumentFields doc = documentFields(s);
        return new RmApiModels.SalidaListRow(
                s.getId(),
                s.getNumeroregistro(),
                s.getRegistroVehiculo() == null ? null : s.getRegistroVehiculo().getId(),
                s.getFecha(),
                s.getHoraCabecera(),
                s.getTransporteId(),
                doc.ocNumero(),
                doc.numeroGuia(),
                s.getGuiaInventarioId(),
                s.getRecepcionEstado(),
                s.getMotivoCancelacion(),
                s.getCanceladoAt(),
                s.getCanceladoPorEmail(),
                s.getCanceladoPorNombre(),
                s.getCreatedAt(),
                s.getLineas());
    }

    private RmApiModels.VehiculoListRow toVehiculoListRow(RmRegistroVehiculo v) {
        return new RmApiModels.VehiculoListRow(
                v.getId(),
                v.getNumeroregistro(),
                v.getTiporegistro(),
                v.getFecha(),
                v.getPlaca(),
                v.getChofer(),
                v.getMarca(),
                v.getCreatedAt());
    }

    private static String trimNullable(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static MultipartFile requireDataPart(MultipartHttpServletRequest request) {
        MultipartFile data = request.getFile("data");
        if (data == null || data.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Falta la parte multipart data (JSON)");
        }
        return data;
    }

    private static String trimHeaderEmail(HttpServletRequest request) {
        String h = request.getHeader("X-User-Email");
        if (h == null) {
            return null;
        }
        String t = h.trim();
        return t.isEmpty() ? null : t.substring(0, Math.min(320, t.length()));
    }

    private static String mediaApiPath(String kind, long recordId, String filename) {
        String k = RmMediaKinds.normalize(kind);
        return "/api/rm/media/" + k + "/" + recordId + "/" + basename(filename);
    }

    private static String toPhotoUrl(String kind, long recordId, String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        String t = stored.trim();
        int marker = t.indexOf("/api/rm/media/");
        if (marker >= 0) {
            String path = t.substring(marker);
            int q = path.indexOf('?');
            return q >= 0 ? path.substring(0, q) : path;
        }
        return mediaApiPath(kind, recordId, t);
    }

    private static String basename(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String t = value.trim();
        int q = t.indexOf('?');
        if (q >= 0) {
            t = t.substring(0, q);
        }
        int slash = Math.max(t.lastIndexOf('/'), t.lastIndexOf('\\'));
        return slash >= 0 ? t.substring(slash + 1) : t;
    }

    private List<String> photoUrls(String kind, long id, List<String> names) {
        return names.stream().map(n -> toPhotoUrl(kind, id, n)).filter(java.util.Objects::nonNull).toList();
    }

    private RmApiModels.RegistroEntradaResponse toEntradaResponse(RmRegistroEntrada e) {
        List<String> docNames = photoFilenameCodec.readList(e.getDocumentoPhotoFilenamesJson());
        Long vehiculoId = e.getRegistroVehiculo() == null ? null : e.getRegistroVehiculo().getId();
        List<RmApiModels.EntradaDetalleResponse> detalles =
                e.getDetalles().stream().map(this::toEntradaDetalle).toList();
        DocumentFields doc = documentFields(e);
        return new RmApiModels.RegistroEntradaResponse(
                e.getId(),
                e.getNumeroregistro(),
                vehiculoId,
                e.getFecha(),
                e.getHora(),
                e.getTipoDocumento(),
                doc.ocNumero(),
                doc.numeroGuia(),
                e.getGuiaInventarioId(),
                null,
                e.getRecepcionEstado(),
                e.getValidadoAt(),
                e.getValidadoPorEmail(),
                e.getChoferValidacionEmpleadoId(),
                e.getChoferValidacionNombre(),
                e.getCreatedAt(),
                e.getCreatedByEmail(),
                e.getObservaciones(),
                e.getMotivoCancelacion(),
                e.getCanceladoAt(),
                e.getCanceladoPorEmail(),
                e.getCanceladoPorNombre(),
                photoUrls(RmMediaKinds.ENTRADA_DOCUMENTO, e.getId(), docNames),
                detalles);
    }

    private RmApiModels.EntradaDetalleResponse toEntradaDetalle(RmRegistroEntradaDetalle d) {
        List<String> names = photoFilenameCodec.readList(d.getPhotoFilenamesJson());
        return new RmApiModels.EntradaDetalleResponse(
                d.getId(),
                d.getMaterial(),
                d.getCantidad(),
                d.getUnidad(),
                d.getCategoriaCodigo(),
                d.getObservaciones(),
                photoUrls(RmMediaKinds.ENTRADA_DETALLE, d.getId(), names));
    }

    private RmApiModels.RegistroSalidaResponse toSalidaResponse(RmRegistroSalida s) {
        List<String> cabNames = photoFilenameCodec.readList(s.getCabeceraPhotoFilenamesJson());
        List<RmApiModels.SalidaDetalleResponse> detalles =
                s.getDetalles().stream().map(this::toSalidaDetalle).toList();
        Long vehiculoId = s.getRegistroVehiculo() == null ? null : s.getRegistroVehiculo().getId();
        DocumentFields doc = documentFields(s);
        return new RmApiModels.RegistroSalidaResponse(
                s.getId(),
                s.getNumeroregistro(),
                vehiculoId,
                s.getFecha(),
                s.getHoraCabecera(),
                s.getOrigen(),
                s.getDestino(),
                doc.numeroGuia(),
                doc.ocNumero(),
                s.getGuiaInventarioId(),
                s.getTransporteId(),
                s.getChoferSalidaEmpleadoId(),
                s.getChoferSalidaNombre(),
                s.getRecepcionEstado(),
                s.getValidadoAt(),
                s.getValidadoPorEmail(),
                s.getChoferValidacionEmpleadoId(),
                s.getChoferValidacionNombre(),
                s.getCreatedAt(),
                s.getCreatedByEmail(),
                s.getObservaciones(),
                s.getMotivoCancelacion(),
                s.getCanceladoAt(),
                s.getCanceladoPorEmail(),
                s.getCanceladoPorNombre(),
                photoUrls(RmMediaKinds.SALIDA_CABECERA, s.getId(), cabNames),
                detalles);
    }

    private RmApiModels.SalidaDetalleResponse toSalidaDetalle(RmRegistroSalidaDetalle d) {
        List<String> names = photoFilenameCodec.readList(d.getPhotoFilenamesJson());
        return new RmApiModels.SalidaDetalleResponse(
                d.getId(),
                d.getHora(),
                d.getMaterialProducto(),
                d.getCantidad(),
                d.getUnidad(),
                d.getCategoriaCodigo(),
                d.getObservaciones(),
                photoUrls(RmMediaKinds.SALIDA_DETALLE, d.getId(), names));
    }

    private RmApiModels.RegistroVehiculoResponse toVehiculoResponse(
            RmRegistroVehiculo v, List<RmApiModels.EntradaListRow> entradas) {
        List<String> names = photoFilenameCodec.readList(v.getPhotoFilenamesJson());
        return new RmApiModels.RegistroVehiculoResponse(
                v.getId(),
                v.getNumeroregistro(),
                v.getTiporegistro(),
                v.getFecha(),
                v.getHoraIngreso(),
                v.getMarca(),
                v.getPlaca(),
                v.getChofer(),
                v.getKilometraje(),
                v.getHoraSalida(),
                v.getCreatedAt(),
                v.getCreatedByEmail(),
                photoUrls(RmMediaKinds.VEHICULO, v.getId(), names),
                entradas);
    }

    private RmApiModels.ActaConformidadResponse toActaResponse(RmActaConformidad a) {
        List<RmApiModels.NcTipoResponse> tipos;
        try {
            List<RmPayloadModels.NcTipo> raw =
                    objectMapper.readValue(a.getTiposJson(), new TypeReference<>() {});
            tipos =
                    raw.stream()
                            .map(t -> new RmApiModels.NcTipoResponse(t.tipo(), t.marcado(), t.detalle()))
                            .toList();
        } catch (Exception ex) {
            tipos = List.of();
        }
        List<String> names = photoFilenameCodec.readList(a.getPhotoFilenamesJson());
        return new RmApiModels.ActaConformidadResponse(
                a.getId(),
                a.getRazonSocialNombre(),
                a.getGuiaRemisionNum(),
                a.getFacturaOrdenCompraNum(),
                a.getTransportistaNombrePlaca(),
                tipos,
                a.getDescripcionAmpliada(),
                a.getDecision(),
                a.getCantidadConformeUnidades(),
                a.getObservacionesDecision(),
                resolveActaEstado(a.getEstado()),
                a.getMotivoCancelacion(),
                a.getCanceladoAt(),
                a.getCanceladoPorEmail(),
                a.getCanceladoPorNombre(),
                a.getCreatedAt(),
                a.getCreatedByEmail(),
                photoUrls(RmMediaKinds.ACTA, a.getId(), names));
    }

    private static String resolveActaEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            return "REGISTRADA";
        }
        return estado.trim();
    }

    private static MediaType probeMediaType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.valueOf("image/webp");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
