package com.allcenter.modulebiesse.controller;

import com.allcenter.modulebiesse.dto.ImpresionStickerRequest;
import com.allcenter.modulebiesse.dto.ImpresionStickerResponse;
import com.allcenter.modulebiesse.service.AuthGatewayService;
import com.allcenter.modulebiesse.service.ImpresionStickerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auditoría de impresiones de stickers (etiquetas de pieza). Cada POST registra un evento con
 * cabecera + 1..n detalles. La IP se resuelve server-side desde {@code X-Forwarded-For} o el
 * remote-addr del request.
 */
@RestController
@RequestMapping("/api/biesse/impresion")
@RequiredArgsConstructor
public class ImpresionStickerController {

    private final ImpresionStickerService service;
    private final AuthGatewayService authGatewayService;

    @PostMapping("/sticker")
    public ResponseEntity<ImpresionStickerResponse> register(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            HttpServletRequest httpRequest,
            @Valid @RequestBody ImpresionStickerRequest request) {
        Long employeeId = authGatewayService.resolveEmployeeId(authorization);
        String clientIp = resolveClientIp(httpRequest);
        return ResponseEntity.ok(service.register(employeeId, clientIp, request));
    }

    @GetMapping("/sticker")
    public ResponseEntity<List<ImpresionStickerResponse>> search(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "100") int limit) {
        authGatewayService.resolveEmployeeId(authorization);
        return ResponseEntity.ok(
                service.search(orderId, parseDate(fromDate, false), parseDate(toDate, true), limit));
    }

    private static String resolveClientIp(HttpServletRequest req) {
        if (req == null) {
            return null;
        }
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return req.getRemoteAddr();
    }

    private static OffsetDateTime parseDate(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return OffsetDateTime.parse(trimmed);
        } catch (Exception ignored) {
            // try ISO local date (YYYY-MM-DD)
        }
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
            return endOfDay
                    ? d.atTime(23, 59, 59).atOffset(ZoneOffset.UTC)
                    : d.atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (Exception ex) {
            return null;
        }
    }
}
