package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.PaleDtos.ApiMessage;
import com.allcenter.modulesystem.dto.PaleDtos.CatalogDto;
import com.allcenter.modulesystem.dto.PaleDtos.CancelPaleRequest;
import com.allcenter.modulesystem.dto.PaleDtos.ClosePaleRequest;
import com.allcenter.modulesystem.dto.PaleDtos.CreatePaleRequest;
import com.allcenter.modulesystem.dto.PaleDtos.CreateSucursalRequest;
import com.allcenter.modulesystem.dto.PaleDtos.CreateUbicacionRequest;
import com.allcenter.modulesystem.dto.PaleDtos.PaleDetailResponse;
import com.allcenter.modulesystem.dto.PaleDtos.PaleAuditEntryDto;
import com.allcenter.modulesystem.dto.PaleDtos.PaleOrderLinkDto;
import com.allcenter.modulesystem.dto.PaleDtos.PaleHeaderDto;
import com.allcenter.modulesystem.dto.PaleDtos.ScanPieceToPaleRequest;
import com.allcenter.modulesystem.dto.PaleDtos.SucursalDto;
import com.allcenter.modulesystem.dto.PaleDtos.UbicacionDto;
import com.allcenter.modulesystem.dto.PaleDtos.UpdatePaleRequest;
import com.allcenter.modulesystem.service.PaleService;
import com.allcenter.modulesystem.support.AuthenticatedEmployeeResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/pallets")
@RequiredArgsConstructor
public class PaleEnvioController {

    private final PaleService paleService;
    private final AuthenticatedEmployeeResolver employeeResolver;

    @GetMapping("/catalogs")
    @PreAuthorize("@portalAuth.canRead()")
    public ResponseEntity<CatalogDto> catalogs() {
        return ResponseEntity.ok(paleService.getCatalogs());
    }

    @GetMapping
    @PreAuthorize("@portalAuth.canRead()")
    public ResponseEntity<List<PaleHeaderDto>> list() {
        return ResponseEntity.ok(paleService.listPallets());
    }

    @GetMapping("/audit")
    @PreAuthorize("@portalAuth.canAudit()")
    public ResponseEntity<List<PaleAuditEntryDto>> audit(
            @RequestParam(required = false) Long paleId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(paleService.listAudit(paleId, action, limit));
    }

    @GetMapping("/by-order/{orderId}")
    @PreAuthorize("@portalAuth.canRead()")
    public ResponseEntity<List<PaleOrderLinkDto>> byOrder(
            @PathVariable long orderId) {
        return ResponseEntity.ok(paleService.findPalesByOrderId(orderId));
    }

    @PostMapping
    @PreAuthorize("@portalAuth.canCreate()")
    public ResponseEntity<PaleDetailResponse> create(
            @Valid @RequestBody CreatePaleRequest request, HttpServletRequest httpRequest) {
        AuthenticatedEmployeeResolver.Context actor =
                employeeResolver.resolve(httpRequest).orElse(null);
        Long branchId = actor == null ? null : actor.branchId();
        Long employeeId = actor == null ? request.createdBy() : actor.employeeId();
        CreatePaleRequest enriched =
                new CreatePaleRequest(
                        request.code(),
                        request.branchId() != null ? request.branchId() : branchId,
                        request.originLocationId(),
                        request.notes(),
                        employeeId);
        return ResponseEntity.ok(
                paleService.createPale(enriched, branchId, trimHeaderEmail(httpRequest)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@portalAuth.canRead()")
    public ResponseEntity<PaleDetailResponse> byId(@PathVariable Long id) {
        return ResponseEntity.ok(paleService.getById(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@portalAuth.canUpdate()")
    public ResponseEntity<PaleDetailResponse> update(
            @PathVariable Long id, @RequestBody(required = false) UpdatePaleRequest request) {
        return ResponseEntity.ok(paleService.updatePale(id, request));
    }

    @DeleteMapping("/{id}/details/{detailId}")
    @PreAuthorize("@portalAuth.canDelete()")
    public ResponseEntity<PaleDetailResponse> deleteDetail(@PathVariable Long id, @PathVariable Long detailId) {
        return ResponseEntity.ok(paleService.removeDetail(id, detailId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@portalAuth.canDelete()")
    public ResponseEntity<ApiMessage> deletePale(@PathVariable Long id) {
        paleService.deletePale(id);
        return ResponseEntity.ok(new ApiMessage(true, "Pale eliminado"));
    }

    @GetMapping("/by-code/{code}")
    @PreAuthorize("@portalAuth.canRead()")
    public ResponseEntity<PaleDetailResponse> byCode(@PathVariable String code) {
        return ResponseEntity.ok(paleService.getByCode(code));
    }

    @PostMapping("/{id}/scan-piece")
    @PreAuthorize("@portalAuth.canCreate()")
    public ResponseEntity<ApiMessage> scanPiece(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long id,
            @Valid @RequestBody ScanPieceToPaleRequest request) {
        return ResponseEntity.ok(paleService.scanPiece(authorization, id, request));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@portalAuth.canClose()")
    public ResponseEntity<ApiMessage> close(
            @PathVariable Long id,
            @RequestBody(required = false) ClosePaleRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                paleService.closePale(
                        id,
                        request == null ? new ClosePaleRequest(null) : request,
                        trimHeaderEmail(httpRequest)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@portalAuth.canCancel()")
    public ResponseEntity<ApiMessage> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) CancelPaleRequest request,
            HttpServletRequest httpRequest) {
        String auth = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        return ResponseEntity.ok(
                paleService.cancelPale(
                        id,
                        request == null ? new CancelPaleRequest(null) : request,
                        auth,
                        trimHeaderEmail(httpRequest)));
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
