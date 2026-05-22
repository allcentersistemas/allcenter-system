package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.GuiaDtos;
import com.allcenter.modulesystem.dto.InventoryDtos;
import com.allcenter.modulesystem.service.GuiaInventoryService;
import com.allcenter.modulesystem.service.InventoryApplicationService;
import com.allcenter.modulesystem.support.AuthenticatedEmployeeResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryApplicationService inventoryService;
    private final GuiaInventoryService guiaInventoryService;
    private final AuthenticatedEmployeeResolver employeeResolver;

    @GetMapping("/items")
    public Page<InventoryDtos.ItemRow> listItems(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return inventoryService.pageItems(q, pageable);
    }

    @GetMapping("/items/{id}")
    public InventoryDtos.ItemDetail getItem(@PathVariable long id) {
        return inventoryService.getItemDetail(id);
    }

    @PostMapping("/items")
    public ResponseEntity<InventoryDtos.Created> createItem(
            @Valid @RequestBody InventoryDtos.CreateItemRequest body, HttpServletRequest request) {
        long id = inventoryService.createItem(body, trimHeaderEmail(request));
        return ResponseEntity.ok(new InventoryDtos.Created(id));
    }

    @PostMapping("/items/{id}/movements")
    public ResponseEntity<InventoryDtos.Created> addMovement(
            @PathVariable long id,
            @Valid @RequestBody InventoryDtos.CreateMovementRequest body,
            HttpServletRequest request) {
        long mid = inventoryService.addMovement(id, body, trimHeaderEmail(request));
        return ResponseEntity.ok(new InventoryDtos.Created(mid));
    }

    @GetMapping("/guias")
    public java.util.List<GuiaDtos.GuiaHeaderDto> listGuias() {
        return guiaInventoryService.listGuias();
    }

    @GetMapping("/guias/pales-escaneados")
    public java.util.List<GuiaDtos.PaleEscaneadoRowDto> listPalesEscaneados(
            @RequestParam(required = false) String q) {
        return guiaInventoryService.listPalesEscaneados(q);
    }

    @GetMapping("/guias/{id}")
    public GuiaDtos.GuiaResponse getGuia(@PathVariable long id) {
        return guiaInventoryService.getGuia(id);
    }

    @PostMapping("/guias")
    public ResponseEntity<GuiaDtos.Created> createGuia(
            @RequestBody GuiaDtos.CreateGuiaRequest body, HttpServletRequest request) {
        AuthenticatedEmployeeResolver.Context actor =
                employeeResolver
                        .resolve(request)
                        .orElseThrow(
                                () ->
                                        new org.springframework.web.server.ResponseStatusException(
                                                org.springframework.http.HttpStatus.UNAUTHORIZED,
                                                "Sesion de empleado requerida para crear guias"));
        GuiaDtos.GuiaResponse created =
                guiaInventoryService.createGuia(withCreadoPor(body, actor.employeeId()), actor.branchId());
        return ResponseEntity.ok(new GuiaDtos.Created(created.guia().guiaId()));
    }

    @PutMapping("/guias/{id}")
    public GuiaDtos.GuiaResponse updateGuia(
            @PathVariable long id, @RequestBody GuiaDtos.UpdateGuiaRequest body) {
        return guiaInventoryService.updateGuia(id, body);
    }

    @PostMapping("/guias/{id}/detalles")
    public GuiaDtos.GuiaResponse addDetalleManual(
            @PathVariable long id,
            @Valid @RequestBody GuiaDtos.AddGuiaDetalleManualRequest body) {
        return guiaInventoryService.addDetalleManual(id, body);
    }

    @PostMapping("/guias/{id}/detalles/pale")
    public GuiaDtos.GuiaResponse addDetallePale(
            @PathVariable long id, @Valid @RequestBody GuiaDtos.AddGuiaDetallePaleRequest body) {
        return guiaInventoryService.addDetalleFromPale(id, body);
    }

    @DeleteMapping("/guias/{id}/detalles/{detalleId}")
    public GuiaDtos.GuiaResponse removeDetalle(@PathVariable long id, @PathVariable long detalleId) {
        return guiaInventoryService.removeDetalle(id, detalleId);
    }

    private static GuiaDtos.CreateGuiaRequest withCreadoPor(GuiaDtos.CreateGuiaRequest body, Long employeeId) {
        Long creadoPor = body.creadoPor() != null ? body.creadoPor() : employeeId;
        return new GuiaDtos.CreateGuiaRequest(
                body.notas(),
                body.destinationBranchId(),
                body.destinationLocationId(),
                creadoPor,
                body.paleIds());
    }

    private static String trimHeaderEmail(HttpServletRequest request) {
        String h = request.getHeader("X-User-Email");
        if (h == null) {
            return null;
        }
        String t = h.trim();
        return t.isEmpty() ? null : t.substring(0, Math.min(320, t.length()));
    }
}
