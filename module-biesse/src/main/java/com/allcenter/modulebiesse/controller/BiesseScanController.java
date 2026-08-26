package com.allcenter.modulebiesse.controller;

import com.allcenter.modulebiesse.dto.PendingPartResponse;
import com.allcenter.modulebiesse.dto.ScanPartRequest;
import com.allcenter.modulebiesse.dto.ScanPieceRequest;
import com.allcenter.modulebiesse.dto.ScanResultResponse;
import com.allcenter.modulebiesse.dto.UpdateOrderRequest;
import com.allcenter.modulebiesse.dto.UserScanStatsResponse;
import com.allcenter.modulebiesse.obras.BiesseObrasRepository;
import com.allcenter.modulebiesse.obras.BiesseObrasSchemaAligner;
import com.allcenter.modulebiesse.service.AuthGatewayService;
import com.allcenter.modulebiesse.service.BiesseScanService;
import com.allcenter.modulebiesse.service.SystemFulfillmentClient;
import com.allcenter.security.BiessePortalRoleAuthorization;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/biesse/scan")
@RequiredArgsConstructor
public class BiesseScanController {

    private final BiesseScanService scanService;
    private final AuthGatewayService authGatewayService;
    private final BiessePortalRoleAuthorization portalAuth;
    private final SystemFulfillmentClient fulfillmentClient;
    private final BiesseObrasRepository obrasRepository;
    private final BiesseObrasSchemaAligner obrasSchemaAligner;

    @GetMapping("/parts/pending")
    public ResponseEntity<List<PendingPartResponse>> getPendingParts(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(defaultValue = "100") int limit) {
        portalAuth.requireRead(authorization);
        return ResponseEntity.ok(scanService.getPendingParts(limit));
    }

    @PostMapping("/parts/scan")
    public ResponseEntity<ScanResultResponse> scanPart(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody ScanPartRequest request) {
        portalAuth.requireCreate(authorization);
        Long employeeId = authGatewayService.resolveEmployeeId(authorization);
        ScanResultResponse result = scanService.scanPart(employeeId, request);
        if (request.equipment() == null || !"PALLET".equalsIgnoreCase(request.equipment().trim())) {
            fulfillmentClient.notifyAndroidScan(authorization, scanService.progressForPart(request.partId()));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/pieces/scan")
    public ResponseEntity<ScanResultResponse> scanPiece(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody ScanPieceRequest request) {
        portalAuth.requireCreate(authorization);
        Long employeeId = authGatewayService.resolveEmployeeId(authorization);
        ScanResultResponse result = scanService.scanPiece(employeeId, request);
        if (request.equipment() == null || !"PALLET".equalsIgnoreCase(request.equipment().trim())) {
            fulfillmentClient.notifyAndroidScan(authorization, scanService.progressForPiece(request.pieceId()));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/pieces/unscan")
    public ResponseEntity<ScanResultResponse> unscanPiece(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody ScanPieceRequest request) {
        // Liberar pieza de pale usa el mismo nivel que escanearla al pale (create).
        // Otras operaciones de unscan (manual) siguen requiriendo admin-ops.
        if (request.equipment() != null && "PALLET".equalsIgnoreCase(request.equipment().trim())) {
            portalAuth.requireCreate(authorization);
        } else {
            portalAuth.requireAdminOps(authorization);
        }
        Long employeeId = authGatewayService.resolveEmployeeId(authorization);
        return ResponseEntity.ok(scanService.unscanPiece(employeeId, request));
    }

    @GetMapping("/users/me/stats")
    public ResponseEntity<UserScanStatsResponse> myStats(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        portalAuth.requireRead(authorization);
        Long employeeId = authGatewayService.resolveEmployeeId(authorization);
        return ResponseEntity.ok(scanService.getMyStats(employeeId));
    }

    @GetMapping("/parts/scanned/me")
    public ResponseEntity<List<Map<String, Object>>> myScannedParts(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "100") int limit) {
        portalAuth.requireRead(authorization);
        Long employeeId = authGatewayService.resolveEmployeeId(authorization);
        return ResponseEntity.ok(scanService.getMyScannedParts(employeeId, fromDate, toDate, limit));
    }

    @GetMapping("/orders")
    public ResponseEntity<List<Map<String, Object>>> getOrders(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        portalAuth.requireRead(authorization);
        return ResponseEntity.ok(scanService.getOrders(orderId, state, q, fromDate, toDate, limit, offset));
    }

    @GetMapping("/ops")
    public ResponseEntity<Map<String, Object>> getOps(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        portalAuth.requireRead(authorization);
        return ResponseEntity.ok(scanService.getOpsPage(q, limit, offset));
    }

    @GetMapping("/audit")
    public ResponseEntity<List<Map<String, Object>>> audit(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long partId,
            @RequestParam(required = false) String orderQ,
            @RequestParam(required = false) String partQ,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        portalAuth.requireAudit(authorization);
        return ResponseEntity.ok(
                scanService.getAudit(orderId, partId, orderQ, partQ, action, limit, offset));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrderDetail(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Long orderId) {
        portalAuth.requireRead(authorization);
        return ResponseEntity.ok(scanService.getOrderDetail(orderId));
    }

    @PatchMapping("/orders/{orderId}")
    public ResponseEntity<Map<String, Object>> updateOrder(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long orderId,
            @RequestBody UpdateOrderRequest request) {
        portalAuth.requireAdminOps(authorization);
        Long employeeId = authGatewayService.resolveEmployeeId(authorization);
        return ResponseEntity.ok(scanService.updateOrder(employeeId, orderId, request.observaciones()));
    }

    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<Map<String, String>> deleteOrder(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Long orderId) {
        portalAuth.requireAdminOps(authorization);
        Long employeeId = authGatewayService.resolveEmployeeId(authorization);
        scanService.deleteOrder(employeeId, orderId);
        return ResponseEntity.ok(Map.of("success", "true", "message", "Orden eliminada"));
    }

    @PostMapping("/orders/{orderId}/complete")
    public ResponseEntity<ScanResultResponse> completeOrder(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long orderId,
            @RequestParam(defaultValue = "MANUAL") String method) {
        portalAuth.requireAdminOps(authorization);
        Long employeeId = authGatewayService.resolveEmployeeId(authorization);
        ScanResultResponse result = scanService.completeOrder(employeeId, orderId, method);
        fulfillmentClient.notifyAndroidScan(authorization, scanService.progressForOrder(orderId));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats/general")
    public ResponseEntity<Map<String, Object>> generalStats(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        portalAuth.requireAudit(authorization);
        return ResponseEntity.ok(scanService.getGeneralStats());
    }

    @GetMapping("/pieces/resolve")
    public ResponseEntity<Map<String, Object>> resolvePiece(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam String code) {
        portalAuth.requireRead(authorization);
        return ResponseEntity.ok(scanService.resolvePieceFromCode(code));
    }

    @GetMapping("/pieces/{pieceId}")
    public ResponseEntity<Map<String, Object>> pieceById(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long pieceId) {
        portalAuth.requireRead(authorization);
        return ResponseEntity.ok(scanService.getPieceById(pieceId));
    }

    /** Trazabilidad OP/obra (portal JWT). El agente CNC escribe vía module-system → integration. */
    @GetMapping("/trazabilidad")
    public ResponseEntity<List<Map<String, Object>>> trazabilidad(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(required = false) String op,
            @RequestParam(required = false) Long orderId,
            @RequestParam(defaultValue = "100") int limit) {
        portalAuth.requireRead(authorization);
        obrasSchemaAligner.ensureReady();
        return ResponseEntity.ok(obrasRepository.listTrazabilidad(op, orderId, limit));
    }
}
