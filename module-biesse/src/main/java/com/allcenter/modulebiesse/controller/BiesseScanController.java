package com.allcenter.modulebiesse.controller;

import com.allcenter.modulebiesse.dto.PendingPartResponse;
import com.allcenter.modulebiesse.dto.ScanPartRequest;
import com.allcenter.modulebiesse.dto.ScanPieceRequest;
import com.allcenter.modulebiesse.dto.ScanResultResponse;
import com.allcenter.modulebiesse.dto.UserScanStatsResponse;
import com.allcenter.modulebiesse.service.AuthGatewayService;
import com.allcenter.modulebiesse.service.BiesseScanService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/parts/pending")
    public ResponseEntity<List<PendingPartResponse>> getPendingParts(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(defaultValue = "100") int limit) {
        authGatewayService.resolveEmployeeId(authorization);
        return ResponseEntity.ok(scanService.getPendingParts(limit));
    }

    @PostMapping("/parts/scan")
    public ResponseEntity<ScanResultResponse> scanPart(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody ScanPartRequest request) {
        Long employeeId = authGatewayService.resolveEmployeeId(authorization);
        return ResponseEntity.ok(scanService.scanPart(employeeId, request));
    }

    @PostMapping("/pieces/scan")
    public ResponseEntity<ScanResultResponse> scanPiece(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody ScanPieceRequest request) {
        Long employeeId = authGatewayService.resolveEmployeeId(authorization);
        return ResponseEntity.ok(scanService.scanPiece(employeeId, request));
    }

    @GetMapping("/users/me/stats")  
    public ResponseEntity<UserScanStatsResponse> myStats(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        Long employeeId = authGatewayService.resolveEmployeeId(authorization);
        return ResponseEntity.ok(scanService.getMyStats(employeeId));
    }

    @GetMapping("/parts/scanned/me")
    public ResponseEntity<List<Map<String, Object>>> myScannedParts(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "100") int limit) {
        Long employeeId = authGatewayService.resolveEmployeeId(authorization);
        return ResponseEntity.ok(scanService.getMyScannedParts(employeeId, fromDate, toDate, limit));
    }

    @GetMapping("/orders")
    public ResponseEntity<List<Map<String, Object>>> getOrders(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        authGatewayService.resolveEmployeeId(authorization);
        return ResponseEntity.ok(scanService.getOrders(state, q, limit, offset));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrderDetail(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Long orderId) {
        authGatewayService.resolveEmployeeId(authorization);
        return ResponseEntity.ok(scanService.getOrderDetail(orderId));
    }

    @PostMapping("/orders/{orderId}/complete")
    public ResponseEntity<ScanResultResponse> completeOrder(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long orderId,
            @RequestParam(defaultValue = "MANUAL") String method) {
        Long employeeId = authGatewayService.resolveEmployeeId(authorization);
        return ResponseEntity.ok(scanService.completeOrder(employeeId, orderId, method));
    }

    @GetMapping("/stats/general")
    public ResponseEntity<Map<String, Object>> generalStats(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        authGatewayService.resolveEmployeeId(authorization);
        return ResponseEntity.ok(scanService.getGeneralStats());
    }

    @GetMapping("/pieces/resolve")
    public ResponseEntity<Map<String, Object>> resolvePiece(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam String code) {
        authGatewayService.resolveEmployeeId(authorization);
        return ResponseEntity.ok(scanService.resolvePieceFromCode(code));
    }

    @GetMapping("/pieces/{pieceId}")
    public ResponseEntity<Map<String, Object>> pieceById(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long pieceId) {
        authGatewayService.resolveEmployeeId(authorization);
        return ResponseEntity.ok(scanService.getPieceById(pieceId));
    }
}
