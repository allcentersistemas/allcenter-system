package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.ClientResponse;
import com.allcenter.modulesystem.dto.OrderDtos;
import com.allcenter.modulesystem.security.EmployeeUserDetails;
import com.allcenter.modulesystem.service.FulfillmentService;
import com.allcenter.modulesystem.service.OrderPersistenceService;
import com.allcenter.modulesystem.service.SeguimientoLiveHub;
import com.allcenter.modulesystem.support.OptimizacionStorageService;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderPersistenceService service;
    private final FulfillmentService fulfillmentService;
    private final OptimizacionStorageService storageService;
    private final SeguimientoLiveHub seguimientoLiveHub;

    public OrderController(
            OrderPersistenceService service,
            FulfillmentService fulfillmentService,
            OptimizacionStorageService storageService,
            SeguimientoLiveHub seguimientoLiveHub) {
        this.service = service;
        this.fulfillmentService = fulfillmentService;
        this.storageService = storageService;
        this.seguimientoLiveHub = seguimientoLiveHub;
    }

    @GetMapping({"/proyectos", "/projects"})
    public List<OrderDtos.ProyectoResumenResponse> listProyectos(
            @AuthenticationPrincipal EmployeeUserDetails principal,
            @RequestParam(defaultValue = "todos") String scope,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String vendedor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
        return service.listProjectsForEmployee(
                principal.getEmployee().getId(),
                scope,
                estado,
                nombre,
                cliente,
                vendedor,
                fechaDesde,
                fechaHasta);
    }

    @PostMapping({"/proyectos", "/projects"})
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDtos.ProyectoResponse createProyecto(@RequestBody OrderDtos.ProyectoPayload payload) {
        return service.saveProyecto(payload);
    }

    @PostMapping("/proyectos/guardar-completo")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDtos.ProyectoConOrdenesResponse saveFullProject(
            @RequestBody OrderDtos.ProyectoCompuestoPayload payload) {
        return service.saveProjectTreeForEmployee(payload);
    }

    @PostMapping("/proyectos/{proyectoId}/ordenes")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDtos.OrdenResponse createOrden(
            @PathVariable Long proyectoId,
            @RequestBody OrderDtos.OrdenPayload payload) {
        return service.saveOrden(proyectoId, payload);
    }

    @PostMapping("/projects/{proyectoId}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDtos.OrdenResponse createOrdenLegacy(
            @PathVariable Long proyectoId,
            @RequestBody OrderDtos.OrdenPayload payload) {
        return service.saveOrden(proyectoId, payload);
    }

    @PutMapping("/ordenes/{ordenId}/detalles")
    public List<OrderDtos.DetalleResponse> replaceDetalles(
            @PathVariable Long ordenId,
            @RequestBody List<OrderDtos.DetallePayload> payload) {
        return service.replaceDetalles(ordenId, payload);
    }

    @PutMapping("/orders/{ordenId}/details")
    public List<OrderDtos.DetalleResponse> replaceDetallesLegacy(
            @PathVariable Long ordenId,
            @RequestBody List<OrderDtos.DetallePayload> payload) {
        return service.replaceDetalles(ordenId, payload);
    }

    @GetMapping("/proyectos/seguimiento")
    public java.util.List<OrderDtos.ProyectoResumenResponse> listSeguimiento() {
        return service.listSeguimiento();
    }

    @GetMapping("/proyectos/seguimiento/ops")
    public java.util.List<OrderDtos.SeguimientoOpResponse> listSeguimientoByOp() {
        return service.listSeguimientoByOp();
    }

    /** Tablero Resumen → Seguimiento: una card por XML/obra (estado_escaneo).
     * @param since fecha mínima yyyy-MM-dd (opcional; default en module-biesse). */
    @GetMapping("/obras/seguimiento")
    public java.util.List<OrderDtos.SeguimientoObraResponse> listSeguimientoObras(
            @RequestParam(required = false) String since) {
        return service.listSeguimientoObras(since);
    }

    /** Canal en vivo (SSE) del tablero de seguimiento. Emite {@code snapshot} y {@code update}. */
    @GetMapping(value = "/obras/seguimiento/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSeguimientoObras(@RequestParam(required = false) String since) {
        return seguimientoLiveHub.connect(since);
    }

    @PostMapping("/obras/{biesseOrderId}/entregado")
    public OrderDtos.FulfillmentActionResponse markObraEntregado(@PathVariable long biesseOrderId) {
        return fulfillmentService.markEntregadoByBiesseOrderId(biesseOrderId);
    }

    @GetMapping("/biesse/obras")
    @PreAuthorize("@portalAuth.canCreate() or @portalAuth.canGestionOrVentasGestion()")
    public Map<String, Object> listBiesseObras(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.listBiesseObras(q, limit, offset);
    }

    @PutMapping("/ordenes/{ordenId}/biesse-obra")
    @PreAuthorize("@portalAuth.canCreate() or @portalAuth.canGestionOrVentasGestion()")
    public OrderDtos.OrdenResponse assignBiesseObra(
            @PathVariable Long ordenId, @RequestBody(required = false) OrderDtos.AsignarBiesseObraPayload payload) {
        Long biesseOrderId = payload == null ? null : payload.biesseOrderId();
        return service.assignBiesseObra(ordenId, biesseOrderId);
    }

    @GetMapping({"/proyectos/{proyectoId}", "/projects/{proyectoId}"})
    public OrderDtos.ProyectoConOrdenesResponse getProyecto(@PathVariable Long proyectoId) {
        return service.getProjectTree(proyectoId);
    }

    @GetMapping("/proyectos/{proyectoId}/cliente")
    @PreAuthorize("@portalAuth.canRead()")
    public ClientResponse getProyectoCliente(@PathVariable Long proyectoId) {
        return service.getPortalClientForProject(proyectoId);
    }

    @DeleteMapping({"/proyectos/{proyectoId}", "/projects/{proyectoId}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@portalAuth.canDeleteGestionProyecto()")
    public void deleteProyecto(@PathVariable Long proyectoId) {
        service.deleteProject(proyectoId);
    }

    @PostMapping("/proyectos/{proyectoId}/capturar")
    public OrderDtos.ProyectoResponse capturarProyecto(
            @AuthenticationPrincipal EmployeeUserDetails principal,
            @PathVariable Long proyectoId) {
        return service.captureProject(principal.getEmployee().getId(), proyectoId);
    }

    @PatchMapping("/proyectos/{proyectoId}/estado")
    @Deprecated
    public OrderDtos.ProyectoResponse updateEstado(
            @PathVariable Long proyectoId,
            @RequestBody OrderDtos.ProyectoEstadoPayload payload) {
        return service.updateEstado(proyectoId, payload == null ? null : payload.estado());
    }

    @PatchMapping("/proyectos/{proyectoId}/gestion")
    @PreAuthorize("@portalAuth.canGestionOrVentasGestion()")
    public OrderDtos.ProyectoResponse updateGestion(
            @PathVariable Long proyectoId,
            @RequestBody OrderDtos.ProyectoGestionPayload payload) {
        return service.updateProyectoGestion(proyectoId, payload);
    }

    @PostMapping("/proyectos/{proyectoId}/vendido")
    public OrderDtos.ProyectoResponse markVendido(
            @AuthenticationPrincipal EmployeeUserDetails principal,
            @PathVariable Long proyectoId) {
        return service.markVendido(principal.getEmployee().getId(), proyectoId);
    }

    @PostMapping("/proyectos/{proyectoId}/entregado")
    public OrderDtos.ProyectoResponse markEntregado(@PathVariable Long proyectoId) {
        return service.advanceFulfillment(
                proyectoId, com.allcenter.modulesystem.model.ProyectoEstado.ENTREGADO, "Marcado entregado manualmente");
    }

    @PostMapping("/fulfillment/android-scan")
    public void androidScanProgress(@RequestBody OrderDtos.AndroidScanProgressPayload payload) {
        if (payload == null) {
            return;
        }
        fulfillmentService.onAndroidScan(
                payload.orderName(),
                payload.bookingCode(),
                Boolean.TRUE.equals(payload.orderComplete()));
    }

    @PostMapping("/fulfillment/android-entregado")
    public OrderDtos.FulfillmentActionResponse markEntregadoFromAndroid(
            @RequestBody OrderDtos.AndroidOrderRefPayload payload) {
        if (payload == null) {
            payload = new OrderDtos.AndroidOrderRefPayload(null, null);
        }
        return fulfillmentService.markEntregadoByOrder(payload.orderName(), payload.bookingCode());
    }

    @PostMapping("/proyectos/{proyectoId}/cancelar")
    @PreAuthorize("@portalAuth.canCancel() or @portalAuth.canVentasGestion() or @portalAuth.canGestion()")
    public OrderDtos.ProyectoResponse cancelarProyecto(@PathVariable Long proyectoId) {
        return service.cancelProjectForEmployee(proyectoId);
    }

    @PatchMapping("/proyectos/{proyectoId}/maquina")
    public OrderDtos.ProyectoResponse updateMaquina(
            @PathVariable Long proyectoId,
            @RequestBody OrderDtos.ProyectoMaquinaPayload payload) {
        return service.updateMaquina(proyectoId, payload == null ? null : payload.maquinaId());
    }

    @PostMapping(value = "/proyectos/{proyectoId}/cotizacion", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OrderDtos.ProyectoResponse uploadCotizacion(
            @AuthenticationPrincipal EmployeeUserDetails principal,
            @PathVariable Long proyectoId,
            @RequestParam("file") MultipartFile file) {
        return service.uploadCotizacion(principal.getEmployee().getId(), proyectoId, file);
    }

    @GetMapping("/proyectos/{proyectoId}/cotizacion")
    public ResponseEntity<Resource> downloadCotizacion(@PathVariable Long proyectoId) {
        OrderDtos.ProyectoConOrdenesResponse tree = service.getProjectTree(proyectoId);
        String filename = tree.project().cotizacionArchivo();
        Resource resource = storageService.loadCotizacion(proyectoId, filename);
        String resolved = storageService.resolveCotizacionFilename(proyectoId, filename);
        String attachmentName =
                resolved != null ? resolved : ("cotizacion-" + proyectoId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachmentName + "\"")
                .body(resource);
    }

    @PostMapping(value = "/proyectos/{proyectoId}/planos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OrderDtos.ProyectoResponse uploadPlanos(
            @AuthenticationPrincipal EmployeeUserDetails principal,
            @PathVariable Long proyectoId,
            @RequestParam("file") MultipartFile file) {
        return service.uploadPlano(principal.getEmployee().getId(), proyectoId, file);
    }

    @GetMapping("/proyectos/{proyectoId}/planos")
    public ResponseEntity<Resource> downloadPlanos(@PathVariable Long proyectoId) {
        OrderDtos.ProyectoConOrdenesResponse tree = service.getProjectTree(proyectoId);
        String filename = tree.project().planoArchivo();
        Resource resource = storageService.loadPlano(proyectoId, filename);
        String resolved = storageService.resolvePlanoFilename(proyectoId, filename);
        String attachmentName =
                resolved != null
                        ? resolved
                        : storageService.planoDownloadName(proyectoId, filename, tree.project().nombre());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachmentName.replace("\"", "") + "\"")
                .body(resource);
    }

    @ExceptionHandler({IllegalArgumentException.class, EntityNotFoundException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(Exception ex) {
        return Map.of("message", ex.getMessage());
    }
}
