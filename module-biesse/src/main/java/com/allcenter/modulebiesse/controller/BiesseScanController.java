package com.allcenter.modulebiesse.controller;

import com.allcenter.modulebiesse.dto.PendingPartResponse;
import com.allcenter.modulebiesse.dto.ScanPartRequest;
import com.allcenter.modulebiesse.dto.ScanPieceRequest;
import com.allcenter.modulebiesse.dto.ScanResultResponse;
import com.allcenter.modulebiesse.dto.UpdateOrderRequest;
import com.allcenter.modulebiesse.dto.UserScanStatsResponse;
import com.allcenter.modulebiesse.service.AuthGatewayService;
import com.allcenter.modulebiesse.service.BiesseScanService;
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
        return ResponseEntity.ok(scanService.scanPart(employeeId, request));
    }

    @PostMapping("/pieces/scan")
    public ResponseEntity<ScanResultResponse> scanPiece(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody ScanPieceRequest request) {
        portalAuth.requireCreate(authorization);
        Long employeeId = authGatewayService.resolveEmployeeId(authorization);
        return ResponseEntity.ok(scanService.scanPiece(employeeId, request));
    }

    @PostMapping("/pieces/unscan")
    public ResponseEntity<ScanResultResponse> unscanPiece(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody ScanPieceRequest request) {
        portalAuth.requireAdminOps(authorization);
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

    @GetMapping("/audit")
    public ResponseEntity<List<Map<String, Object>>> audit(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long partId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        portalAuth.requireAudit(authorization);
        return ResponseEntity.ok(scanService.getAudit(orderId, partId, action, limit, offset));
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
        return ResponseEntity.ok(scanService.completeOrder(employeeId, orderId, method));
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
}
