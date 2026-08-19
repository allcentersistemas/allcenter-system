package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.model.OdooWebhookEvent;
import com.allcenter.modulesystem.service.OdooWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OdooWebhookController {

    private final OdooWebhookService odooWebhookService;

    @PostMapping(
            value = "/webhook/odoo-orden-compra",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE, MediaType.ALL_VALUE})
    public ResponseEntity<Map<String, Object>> ordenCompra(HttpServletRequest request) throws IOException {
        return ingest("ORDEN_COMPRA", request);
    }

    @PostMapping(
            value = "/webhook/odoo-pago",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE, MediaType.ALL_VALUE})
    public ResponseEntity<Map<String, Object>> pago(HttpServletRequest request) throws IOException {
        return ingest("PAGO", request);
    }

    @GetMapping("/api/admin/odoo-webhooks")
    @PreAuthorize("@portalAuth.isMaster()")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String tipo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<OdooWebhookEvent> result = odooWebhookService.list(tipo, page, size);
        return ResponseEntity.ok(
                Map.of(
                        "items",
                        result.getContent().stream().map(OdooWebhookController::toDto).toList(),
                        "page",
                        result.getNumber(),
                        "size",
                        result.getSize(),
                        "totalElements",
                        result.getTotalElements()));
    }

    private ResponseEntity<Map<String, Object>> ingest(String tipo, HttpServletRequest request) throws IOException {
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        OdooWebhookEvent saved =
                odooWebhookService.ingest(tipo, body, request.getContentType(), ip);
        return ResponseEntity.ok(
                Map.of(
                        "ok",
                        true,
                        "id",
                        saved.getId(),
                        "actionTaken",
                        saved.getActionTaken() == null ? "" : saved.getActionTaken(),
                        "matchedProyectoId",
                        saved.getMatchedProyectoId() == null ? 0 : saved.getMatchedProyectoId()));
    }

    private static Map<String, Object> toDto(OdooWebhookEvent e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.getId());
        row.put("tipo", e.getTipo());
        row.put("receivedAt", e.getReceivedAt() == null ? Instant.EPOCH.toString() : e.getReceivedAt().toString());
        row.put("remoteIp", e.getRemoteIp() == null ? "" : e.getRemoteIp());
        row.put("contentType", e.getContentType() == null ? "" : e.getContentType());
        row.put("payload", e.getPayload() == null ? "" : e.getPayload());
        row.put("matchedProyectoId", e.getMatchedProyectoId() == null ? 0 : e.getMatchedProyectoId());
        row.put("actionTaken", e.getActionTaken() == null ? "" : e.getActionTaken());
        row.put("note", e.getNote() == null ? "" : e.getNote());
        row.put("odooRecordId", e.getOdooRecordId() == null ? 0 : e.getOdooRecordId());
        row.put("odooModel", e.getOdooModel() == null ? "" : e.getOdooModel());
        row.put("odooName", e.getOdooName() == null ? "" : e.getOdooName());
        row.put("odooDisplayName", e.getOdooDisplayName() == null ? "" : e.getOdooDisplayName());
        row.put("partnerId", e.getPartnerId() == null ? 0 : e.getPartnerId());
        row.put("partnerName", e.getPartnerName() == null ? "" : e.getPartnerName());
        row.put("dateOrder", e.getDateOrder() == null ? "" : e.getDateOrder());
        row.put("amountTotal", e.getAmountTotal() == null ? "" : e.getAmountTotal());
        row.put("odooState", e.getOdooState() == null ? "" : e.getOdooState());
        return row;
    }
}
