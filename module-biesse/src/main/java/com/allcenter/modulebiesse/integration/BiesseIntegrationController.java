package com.allcenter.modulebiesse.integration;

import com.allcenter.modulebiesse.obras.BiesseObrasRepository;
import com.allcenter.modulebiesse.obras.BiesseObrasSchemaAligner;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * APIs de obras/XML para que module-system orqueste el agente CNC.
 * Auth: JWT portal o header {@code X-Internal-Token}.
 */
@RestController
@RequestMapping("/api/biesse/scan/integration")
@RequiredArgsConstructor
public class BiesseIntegrationController {

    private final BiesseObrasRepository obrasRepository;
    private final BiesseObrasSchemaAligner schemaAligner;
    private final BiesseInternalAuth internalAuth;

    public record TrazabilidadRequest(
            String opCodigo,
            Long orderId,
            String orderName,
            String estado,
            String accion,
            String detalle,
            Integer piezas,
            Integer partes,
            String usuario) {}

    @GetMapping("/orders/by-job")
    public ResponseEntity<Map<String, Object>> orderByJob(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = BiesseInternalAuth.HEADER_INTERNAL, required = false) String internalToken,
            @RequestParam String jobName) {
        internalAuth.requireRead(authorization, internalToken);
        schemaAligner.ensureReady();
        Map<String, Object> order = obrasRepository.findOrderForJob(jobName);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada para job");
        }
        return ResponseEntity.ok(order);
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Map<String, Object>> orderById(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = BiesseInternalAuth.HEADER_INTERNAL, required = false) String internalToken,
            @PathVariable long orderId) {
        internalAuth.requireRead(authorization, internalToken);
        schemaAligner.ensureReady();
        Map<String, Object> order = obrasRepository.findOrderById(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada");
        }
        return ResponseEntity.ok(order);
    }

    @PostMapping("/orders/{orderId}/produccion")
    public ResponseEntity<Map<String, Object>> markProduccion(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = BiesseInternalAuth.HEADER_INTERNAL, required = false) String internalToken,
            @PathVariable long orderId) {
        internalAuth.requireWrite(authorization, internalToken);
        schemaAligner.ensureReady();
        boolean changed = obrasRepository.markOrderProduccion(orderId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("changed", changed);
        out.put("orderId", orderId);
        out.put("estado", "PRODUCCION");
        return ResponseEntity.ok(out);
    }

    @PostMapping("/trazabilidad")
    public ResponseEntity<Map<String, String>> trazabilidad(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = BiesseInternalAuth.HEADER_INTERNAL, required = false) String internalToken,
            @RequestBody TrazabilidadRequest body) {
        internalAuth.requireWrite(authorization, internalToken);
        schemaAligner.ensureReady();
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body requerido");
        }
        obrasRepository.registrarTrazabilidad(
                body.opCodigo(),
                body.orderId(),
                body.orderName(),
                body.estado() != null ? body.estado() : "PRODUCCION",
                body.accion() != null ? body.accion() : "EVENTO",
                body.detalle(),
                body.piezas() != null ? body.piezas() : 0,
                body.partes() != null ? body.partes() : 0,
                body.usuario());
        return ResponseEntity.ok(Map.of("ok", "true"));
    }

    @GetMapping("/trazabilidad")
    public ResponseEntity<List<Map<String, Object>>> listTrazabilidad(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = BiesseInternalAuth.HEADER_INTERNAL, required = false) String internalToken,
            @RequestParam(required = false) String op,
            @RequestParam(required = false) Long orderId,
            @RequestParam(defaultValue = "100") int limit) {
        internalAuth.requireRead(authorization, internalToken);
        schemaAligner.ensureReady();
        return ResponseEntity.ok(obrasRepository.listTrazabilidad(op, orderId, limit));
    }

    @GetMapping("/parts/for-osi")
    public ResponseEntity<Map<String, Object>> partForOsi(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = BiesseInternalAuth.HEADER_INTERNAL, required = false) String internalToken,
            @RequestParam long orderId,
            @RequestParam String osiPart,
            @RequestParam(required = false) String machineName) {
        internalAuth.requireRead(authorization, internalToken);
        schemaAligner.ensureReady();

        Map<String, Object> order = obrasRepository.findOrderById(orderId);
        String orderName = order != null ? str(order.get("ordername")) : ("#" + orderId);
        String booking = order != null ? str(order.get("bookingcode")) : null;
        Map<String, Object> part = obrasRepository.findPartForOsi(orderId, osiPart);

        Map<String, Object> out = new LinkedHashMap<>();
        if (part == null) {
            String unitCode = orderName + "-" + osiPart.replaceAll("\\s+", "");
            out.put("mapStatus", "UNMAPPED");
            out.put("unitCode", unitCode);
            out.put("partId", null);
            out.put("partCode", osiPart);
            out.put("material", null);
            out.put(
                    "zpl",
                    SimpleZplBuilder.build(orderName, booking, osiPart, "", unitCode, machineName));
            return ResponseEntity.ok(out);
        }

        long partId = ((Number) part.get("partid")).longValue();
        int partNumber = intOrZero(part.get("partnumber"));
        if (partNumber <= 0) {
            Integer parsed = BiesseObrasRepository.parsePartNumber(osiPart);
            partNumber = parsed != null ? parsed : 0;
        }
        int pieceNum = obrasRepository.nextPieceNumber(partId);
        String unitCode = orderName + "-P" + partNumber + "-" + pieceNum;
        out.put("mapStatus", "MAPPED");
        out.put("unitCode", unitCode);
        out.put("partId", partId);
        out.put("partCode", part.get("partcode"));
        out.put("material", part.get("material"));
        out.put(
                "zpl",
                SimpleZplBuilder.build(
                        orderName,
                        booking,
                        str(part.get("partcode")),
                        str(part.get("material")),
                        unitCode,
                        machineName));
        return ResponseEntity.ok(out);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int intOrZero(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
