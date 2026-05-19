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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
    private final PhotoFilenameCodec photoFilenameCodec;
    private final RmStorageService storageService;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/registros-entrada", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RmApiModels.CreatedEntrada postEntrada(MultipartHttpServletRequest request) throws IOException {
        MultipartFile data = requireDataPart(request);
        List<MultipartFile> photos = request.getFiles("photos");
        String user = trimHeaderEmail(request);
        return registroService.createRegistroEntrada(data.getBytes(), photos, user);
    }

    @GetMapping("/registros-entrada")
    public Page<RmApiModels.EntradaListRow> listEntradas(
            @PageableDefault(size = 20) Pageable pageable, HttpServletRequest request) {
        return registroService
                .pageEntradas(pageable)
                .map(e -> new RmApiModels.EntradaListRow(
                        e.getId(),
                        e.getFecha(),
                        e.getHora(),
                        e.getTransporteId(),
                        e.getRecepcionEstado(),
                        e.getCreatedAt(),
                        e.getLineas()));
    }

    @GetMapping("/registros-entrada/{id}")
    public RmApiModels.RegistroEntradaResponse getEntrada(@PathVariable long id, HttpServletRequest request) {
        return toEntradaResponse(registroService.getEntrada(id), request);
    }

    @PostMapping(value = "/registros-salida", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RmApiModels.Created postSalida(MultipartHttpServletRequest request) throws IOException {
        MultipartFile data = requireDataPart(request);
        List<MultipartFile> photos = request.getFiles("photos");
        String user = trimHeaderEmail(request);
        return registroService.createRegistroSalida(data.getBytes(), photos, user);
    }

    @GetMapping("/registros-salida")
    public Page<RmApiModels.SalidaListRow> listSalidas(
            @PageableDefault(size = 20) Pageable pageable, HttpServletRequest request) {
        return registroService
                .pageSalidas(pageable)
                .map(s -> new RmApiModels.SalidaListRow(
                        s.getId(),
                        s.getFecha(),
                        s.getHoraCabecera(),
                        s.getTransporteId(),
                        s.getRecepcionEstado(),
                        s.getCreatedAt(),
                        s.getLineas()));
    }

    @GetMapping("/registros-salida/{id}")
    public RmApiModels.RegistroSalidaResponse getSalida(@PathVariable long id, HttpServletRequest request) {
        return toSalidaResponse(registroService.getSalida(id), request);
    }

    @PostMapping(value = "/registros-vehiculo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RmApiModels.Created postVehiculo(MultipartHttpServletRequest request) throws IOException {
        MultipartFile data = requireDataPart(request);
        List<MultipartFile> photos = request.getFiles("photos");
        String user = trimHeaderEmail(request);
        return registroService.createRegistroVehiculo(data.getBytes(), photos, user);
    }

    @GetMapping("/registros-vehiculo")
    public Page<RmApiModels.VehiculoListRow> listVehiculos(
            @PageableDefault(size = 20) Pageable pageable, HttpServletRequest request) {
        return registroService
                .pageVehiculos(pageable)
                .map(v -> new RmApiModels.VehiculoListRow(
                        v.getId(), v.getFecha(), v.getPlaca(), v.getChofer(), v.getMarca(), v.getCreatedAt()));
    }

    @GetMapping("/registros-vehiculo/{id}")
    public RmApiModels.RegistroVehiculoResponse getVehiculo(@PathVariable long id, HttpServletRequest request) {
        return toVehiculoResponse(registroService.getVehiculo(id), request);
    }

    @PostMapping(value = "/actas-conformidad", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RmApiModels.Created postActa(MultipartHttpServletRequest request) throws IOException {
        MultipartFile data = requireDataPart(request);
        List<MultipartFile> photos = request.getFiles("photos");
        String user = trimHeaderEmail(request);
        return registroService.createActaConformidad(data.getBytes(), photos, user);
    }

    @GetMapping("/actas-conformidad")
    public Page<RmApiModels.ActaListRow> listActas(
            @PageableDefault(size = 20) Pageable pageable, HttpServletRequest request) {
        return registroService
                .pageActas(pageable)
                .map(a -> new RmApiModels.ActaListRow(
                        a.getId(), a.getRazonSocialNombre(), a.getDecision(), a.getCreatedAt()));
    }

    @GetMapping("/actas-conformidad/{id}")
    public RmApiModels.ActaConformidadResponse getActa(@PathVariable long id, HttpServletRequest request) {
        return toActaResponse(registroService.getActa(id), request);
    }

    @GetMapping("/media/{kind}/{recordId}/{filename:.+}")
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

    /** Ruta relativa al API (el portal la prefija con /api-system). */
    private static String mediaApiPath(String kind, long recordId, String filename) {
        String k = RmMediaKinds.normalize(kind);
        return "/api/rm/media/" + k + "/" + recordId + "/" + filename;
    }

    private List<String> photoUrls(String kind, long id, List<String> names) {
        return names.stream().map(n -> mediaApiPath(kind, id, n)).toList();
    }

    private RmApiModels.RegistroEntradaResponse toEntradaResponse(RmRegistroEntrada e, HttpServletRequest request) {
        List<String> cabVehNames = photoFilenameCodec.readList(e.getCabeceraVehiculoPhotoFilenamesJson());
        List<RmApiModels.EntradaDetalleResponse> detalles =
                e.getDetalles().stream()
                        .map(d -> toEntradaDetalle(d, request))
                        .toList();
        return new RmApiModels.RegistroEntradaResponse(
                e.getId(),
                e.getFecha(),
                e.getHora(),
                e.getTransporteId(),
                e.getChoferIngresoEmpleadoId(),
                e.getChoferIngresoNombre(),
                e.getKilometrajeIngreso(),
                e.getRecepcionEstado(),
                e.getValidadoAt(),
                e.getValidadoPorEmail(),
                e.getChoferValidacionEmpleadoId(),
                e.getChoferValidacionNombre(),
                e.getCreatedAt(),
                e.getCreatedByEmail(),
                photoUrls(RmMediaKinds.ENTRADA_CABECERA_VEHICULO, e.getId(), cabVehNames),
                detalles);
    }

    private RmApiModels.EntradaDetalleResponse toEntradaDetalle(RmRegistroEntradaDetalle d, HttpServletRequest request) {
        List<String> names = photoFilenameCodec.readList(d.getPhotoFilenamesJson());
        return new RmApiModels.EntradaDetalleResponse(
                d.getId(),
                d.getProveedor(),
                d.getOcNumero(),
                d.getGuiaNumero(),
                d.getMaterial(),
                d.getColorModelo(),
                d.getCantidadRecibida(),
                d.getUnidad(),
                photoUrls(RmMediaKinds.ENTRADA_DETALLE, d.getId(), names));
    }

    private RmApiModels.RegistroSalidaResponse toSalidaResponse(RmRegistroSalida s, HttpServletRequest request) {
        List<String> cabNames = photoFilenameCodec.readList(s.getCabeceraPhotoFilenamesJson());
        List<RmApiModels.SalidaDetalleResponse> detalles =
                s.getDetalles().stream().map(d -> toSalidaDetalle(d, request)).toList();
        return new RmApiModels.RegistroSalidaResponse(
                s.getId(),
                s.getFecha(),
                s.getHoraCabecera(),
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
                photoUrls(RmMediaKinds.SALIDA_CABECERA, s.getId(), cabNames),
                detalles);
    }

    private RmApiModels.SalidaDetalleResponse toSalidaDetalle(RmRegistroSalidaDetalle d, HttpServletRequest request) {
        List<String> names = photoFilenameCodec.readList(d.getPhotoFilenamesJson());
        return new RmApiModels.SalidaDetalleResponse(
                d.getId(),
                d.getHora(),
                d.getDestino(),
                d.getNoRqmVale(),
                d.getNoGuia(),
                d.getMaterialProducto(),
                d.getCantidad(),
                d.getUnidad(),
                d.getRecibeFirma(),
                d.getEntregaRci(),
                photoUrls(RmMediaKinds.SALIDA_DETALLE, d.getId(), names));
    }

    private RmApiModels.RegistroVehiculoResponse toVehiculoResponse(RmRegistroVehiculo v, HttpServletRequest request) {
        List<String> names = photoFilenameCodec.readList(v.getPhotoFilenamesJson());
        List<RmApiModels.VehiculoProductoResponse> productos;
        try {
            List<RmPayloadModels.VehiculoProducto> raw =
                    objectMapper.readValue(
                            v.getProductosJson() == null || v.getProductosJson().isBlank()
                                    ? "[]"
                                    : v.getProductosJson(),
                            new TypeReference<>() {});
            productos =
                    raw.stream()
                            .map(
                                    x ->
                                            new RmApiModels.VehiculoProductoResponse(
                                                    x.materialProducto(), x.cantidad(), x.unidad()))
                            .toList();
        } catch (Exception ex) {
            productos = List.of();
        }
        return new RmApiModels.RegistroVehiculoResponse(
                v.getId(),
                v.getFecha(),
                v.getHoraIngreso(),
                v.getMarca(),
                v.getPlaca(),
                v.getChofer(),
                v.getKilometraje(),
                v.getHoraSalida(),
                v.getCreatedAt(),
                v.getCreatedByEmail(),
                productos,
                photoUrls(RmMediaKinds.VEHICULO, v.getId(), names));
    }

    private RmApiModels.ActaConformidadResponse toActaResponse(RmActaConformidad a, HttpServletRequest request) {
        List<RmApiModels.NcTipoResponse> tipos;
        try {
            List<com.allcenter.modulesystem.dto.RmPayloadModels.NcTipo> raw =
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
                a.getCreatedAt(),
                a.getCreatedByEmail(),
                photoUrls(RmMediaKinds.ACTA, a.getId(), names));
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
