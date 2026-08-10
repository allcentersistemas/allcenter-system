package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.InventoryDtos;
import com.allcenter.modulesystem.dto.OrderDtos;
import com.allcenter.modulesystem.dto.PlanillaAiExtractDtos;
import com.allcenter.modulesystem.exception.BadRequestException;
import com.allcenter.modulesystem.security.ClientUserDetails;
import com.allcenter.modulesystem.service.AppConfigService;
import com.allcenter.modulesystem.service.ClientOptimizacionCatalogService;
import com.allcenter.modulesystem.service.MaquinaOptimizacionService;
import com.allcenter.modulesystem.service.OrderPersistenceService;
import com.allcenter.modulesystem.service.PlanillaAiVisionService;
import com.allcenter.modulesystem.support.OptimizacionStorageService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/client/optimizacion")
public class ClientOptimizacionController {

    private final OrderPersistenceService service;
    private final ClientOptimizacionCatalogService catalogService;
    private final MaquinaOptimizacionService maquinaService;
    private final OptimizacionStorageService storageService;
    private final AppConfigService appConfigService;
    private final PlanillaAiVisionService planillaAiVisionService;

    public ClientOptimizacionController(
            OrderPersistenceService service,
            ClientOptimizacionCatalogService catalogService,
            MaquinaOptimizacionService maquinaService,
            OptimizacionStorageService storageService,
            AppConfigService appConfigService,
            PlanillaAiVisionService planillaAiVisionService) {
        this.service = service;
        this.catalogService = catalogService;
        this.maquinaService = maquinaService;
        this.storageService = storageService;
        this.appConfigService = appConfigService;
        this.planillaAiVisionService = planillaAiVisionService;
    }

    @GetMapping("/features")
    public PlanillaAiExtractDtos.FeaturesResponse features(
            @AuthenticationPrincipal ClientUserDetails principal) {
        return new PlanillaAiExtractDtos.FeaturesResponse(appConfigService.isAiVisionEnabled());
    }

    @PostMapping(value = "/extract-medidas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PlanillaAiExtractDtos.ExtractResponse extractMedidas(
            @AuthenticationPrincipal ClientUserDetails principal, @RequestPart("file") MultipartFile file) {
        return planillaAiVisionService.extractFromImage(principal.getClientUser().getId(), file);
    }

    @GetMapping("/catalogos/kardex")
    public InventoryDtos.OptimizacionKardexCatalog listKardexCatalog() {
        return catalogService.listKardexCatalog();
    }

    @GetMapping("/maquinas")
    public List<OrderDtos.MaquinaResponse> listMaquinas() {
        return maquinaService.listActive();
    }

    @GetMapping("/proyectos")
    public List<OrderDtos.ProyectoResumenResponse> listProyectos(
            @AuthenticationPrincipal ClientUserDetails principal) {
        return service.listProjectsForClient(principal.getClientUser().getId());
    }

    @GetMapping("/proyectos/por-nombre")
    public ResponseEntity<OrderDtos.ProyectoResumenResponse> findProyectoByNombre(
            @AuthenticationPrincipal ClientUserDetails principal, @RequestParam String nombre) {
        OrderDtos.ProyectoResumenResponse found =
                service.findProjectByNameForClient(principal.getClientUser().getId(), nombre);
        if (found == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(found);
    }

    @GetMapping("/proyectos/{proyectoId}")
    public OrderDtos.ProyectoConOrdenesResponse getProyecto(
            @AuthenticationPrincipal ClientUserDetails principal, @PathVariable Long proyectoId) {
        return service.getProjectTreeForClient(principal.getClientUser().getId(), proyectoId);
    }

    @PostMapping("/proyectos/guardar-completo")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDtos.ProyectoConOrdenesResponse saveFullProject(
            @AuthenticationPrincipal ClientUserDetails principal,
            @RequestBody OrderDtos.ProyectoCompuestoPayload payload) {
        return service.saveProjectTreeForClient(principal.getClientUser().getId(), payload);
    }

    @PatchMapping("/proyectos/{proyectoId}/maquina")
    public OrderDtos.ProyectoResponse updateMaquina(
            @AuthenticationPrincipal ClientUserDetails principal,
            @PathVariable Long proyectoId,
            @RequestBody OrderDtos.ProyectoMaquinaPayload payload) {
        return service.updateMaquinaForClient(
                principal.getClientUser().getId(),
                proyectoId,
                payload == null ? null : payload.maquinaId());
    }

    @PostMapping("/proyectos/{proyectoId}/cancelar")
    public OrderDtos.ProyectoResponse cancelarProyecto(
            @AuthenticationPrincipal ClientUserDetails principal,
            @PathVariable Long proyectoId) {
        return service.cancelProjectForClient(principal.getClientUser().getId(), proyectoId);
    }

    @GetMapping("/proyectos/{proyectoId}/cotizacion")
    public ResponseEntity<Resource> downloadCotizacion(
            @AuthenticationPrincipal ClientUserDetails principal,
            @PathVariable Long proyectoId) {
        String storedFilename =
                service.getCotizacionStoredFilenameForClient(principal.getClientUser().getId(), proyectoId);
        Resource resource = storageService.loadCotizacion(proyectoId, storedFilename);
        String resolved = storageService.resolveCotizacionFilename(proyectoId, storedFilename);
        String downloadName =
                resolved != null
                        ? resolved
                        : (storedFilename == null || storedFilename.isBlank()
                                ? "cotizacion-" + proyectoId + ".pdf"
                                : storedFilename.trim());
        String contentType = storageService.cotizacionContentType(downloadName);
        String safeName = downloadName == null ? ("cotizacion-" + proyectoId + ".pdf") : downloadName.replace("\"", "");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"")
                .body(resource);
    }

    /** Vista inline de planos (PDF). Sin descarga para el cliente. */
    @GetMapping("/proyectos/{proyectoId}/planos/view")
    public ResponseEntity<Resource> viewPlanos(
            @AuthenticationPrincipal ClientUserDetails principal,
            @PathVariable Long proyectoId) {
        String storedFilename =
                service.getPlanoStoredFilenameForClient(principal.getClientUser().getId(), proyectoId);
        Resource resource = storageService.loadPlano(proyectoId, storedFilename);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"planos.pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(resource);
    }

    @GetMapping("/plantilla")
    public ResponseEntity<Resource> downloadPlantilla(@AuthenticationPrincipal ClientUserDetails principal) {
        Resource resource = storageService.loadPlantilla();
        String name = storageService.plantillaDownloadName().replace("\"", "");
        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .body(resource);
    }

    @ExceptionHandler({IllegalArgumentException.class, EntityNotFoundException.class, BadRequestException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(Exception ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        String message =
                ex.getReason() != null && !ex.getReason().isBlank()
                        ? ex.getReason()
                        : "No se pudo obtener la cotización.";
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", message));
    }
}
