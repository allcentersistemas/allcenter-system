package com.allcenter.modulebiesse.controller;

import com.allcenter.modulebiesse.dto.ScanInterpretRequest;
import com.allcenter.modulebiesse.dto.ScanInterpretResponse;
import com.allcenter.modulebiesse.service.BiesseScanService;
import com.allcenter.security.BiessePortalRoleAuthorization;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rutas legacy usadas por la app Android ({@code OsiApiService}).
 * Alias de la lógica de escaneo en {@code /api/biesse/scan}.
 */
@RestController
@RequestMapping("/api/osi/scan")
@RequiredArgsConstructor
public class OsiScanController {

    private final BiesseScanService scanService;
    private final BiessePortalRoleAuthorization portalAuth;

    @PostMapping("/interpret")
    public ResponseEntity<ScanInterpretResponse> interpret(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody ScanInterpretRequest request) {
        portalAuth.requireCreate(authorization);
        return ResponseEntity.ok(
                scanService.interpretScan(
                        request.code(), request.currentOrderId(), request.confirmOrderSwitch()));
    }
}
