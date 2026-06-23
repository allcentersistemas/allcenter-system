package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.OrderDtos;
import com.allcenter.modulesystem.security.EmployeeUserDetails;
import com.allcenter.modulesystem.service.OrderPersistenceService;
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

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderPersistenceService service;
    private final OptimizacionStorageService storageService;

    public OrderController(OrderPersistenceService service, OptimizacionStorageService storageService) {
        this.service = service;
        this.storageService = storageService;
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

    @GetMapping({"/proyectos/{proyectoId}", "/projects/{proyectoId}"})
    public OrderDtos.ProyectoConOrdenesResponse getProyecto(@PathVariable Long proyectoId) {
        return service.getProjectTree(proyectoId);
    }

    @DeleteMapping({"/proyectos/{proyectoId}", "/projects/{proyectoId}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@portalAuth.canDelete()")
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
    public OrderDtos.ProyectoResponse updateEstado(
            @PathVariable Long proyectoId,
            @RequestBody OrderDtos.ProyectoEstadoPayload payload) {
        return service.updateEstado(proyectoId, payload == null ? null : payload.estado());
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
        if (filename == null || filename.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = storageService.loadCotizacion(proyectoId, filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cotizacion-" + proyectoId + "\"")
                .body(resource);
    }

    @ExceptionHandler({IllegalArgumentException.class, EntityNotFoundException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(Exception ex) {
        return Map.of("message", ex.getMessage());
    }
}
