package com.allcenter.modulebiesse.integration;

import com.allcenter.modulebiesse.obras.BiesseObrasRepository;
import com.allcenter.modulebiesse.obras.BiesseObrasSchemaAligner;
import com.allcenter.modulebiesse.repository.BiesseScanRepository;
import com.allcenter.modulebiesse.service.AgentCutSyncService;
import com.allcenter.modulebiesse.service.BiesseScanService;
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
 * APIs de obras/XML para que module-system orqueste el agente seccionador.
 * Auth: JWT portal o header {@code X-Internal-Token}.
 */
@RestController
@RequestMapping("/api/biesse/scan/integration")
@RequiredArgsConstructor
public class BiesseIntegrationController {

    private final BiesseObrasRepository obrasRepository;
    private final BiesseObrasSchemaAligner schemaAligner;
    private final BiesseInternalAuth internalAuth;
    private final BiesseScanService scanService;
    private final BiesseScanRepository scanRepository;
    private final AgentCutSyncService agentCutSyncService;

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

    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> listOrders(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = BiesseInternalAuth.HEADER_INTERNAL, required = false) String internalToken,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        internalAuth.requireRead(authorization, internalToken);
        schemaAligner.ensureReady();
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int safeOffset = Math.max(0, offset);
        List<Map<String, Object>> items =
                scanService.getOrders(null, null, q, null, null, safeLimit, safeOffset);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("totalCount", items.size());
        return ResponseEntity.ok(out);
    }

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

    @GetMapping("/ops/{opCodigo}")
    public ResponseEntity<Map<String, Object>> opSummary(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = BiesseInternalAuth.HEADER_INTERNAL, required = false) String internalToken,
            @PathVariable String opCodigo) {
        internalAuth.requireRead(authorization, internalToken);
        schemaAligner.ensureReady();
        if (opCodigo == null || opCodigo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "opCodigo requerido");
        }
        List<Map<String, Object>> obras = scanRepository.findObrasByOp(opCodigo.trim());
        int piezasTot = 0;
        int piezasEsc = 0;
        int partesTot = 0;
        int partesEsc = 0;
        for (Map<String, Object> o : obras) {
            piezasTot += numberInt(o.get("piezas_totales"));
            piezasEsc += numberInt(o.get("piezas_escaneadas"));
            partesTot += numberInt(o.get("total_partes"));
            partesEsc += numberInt(o.get("partes_escaneadas"));
        }
        double pct;
        String avance;
        if (piezasTot > 0) {
            pct = Math.round(piezasEsc * 1000.0 / piezasTot) / 10.0;
            avance = piezasEsc + "/" + piezasTot + " piezas";
        } else if (partesTot > 0) {
            pct = Math.round(partesEsc * 1000.0 / partesTot) / 10.0;
            avance = partesEsc + "/" + partesTot + " partes";
        } else {
            pct = 0;
            avance = "0/0";
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("op_codigo", opCodigo.trim());
        out.put("total_obras", obras.size());
        out.put("porcentaje", pct);
        out.put("avance_label", avance);
        out.put("obras", obras);
        return ResponseEntity.ok(out);
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

    @GetMapping("/seguimiento")
    public ResponseEntity<List<Map<String, Object>>> listSeguimiento(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = BiesseInternalAuth.HEADER_INTERNAL, required = false) String internalToken,
            @RequestParam(defaultValue = "300") int limit,
            @RequestParam(required = false) String since) {
        internalAuth.requireRead(authorization, internalToken);
        schemaAligner.ensureReady();
        java.time.LocalDate sinceDate = null;
        if (since != null && !since.isBlank()) {
            try {
                sinceDate = java.time.LocalDate.parse(since.trim());
            } catch (Exception ex) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "since debe ser yyyy-MM-dd");
            }
        }
        return ResponseEntity.ok(obrasRepository.listSeguimientoObras(limit, sinceDate));
    }

    @PostMapping("/orders/{orderId}/entregado")
    public ResponseEntity<Map<String, Object>> markEntregado(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = BiesseInternalAuth.HEADER_INTERNAL, required = false) String internalToken,
            @PathVariable long orderId,
            @RequestParam(required = false) String usuario) {
        internalAuth.requireWrite(authorization, internalToken);
        schemaAligner.ensureReady();
        boolean changed = obrasRepository.markOrderEntregado(orderId, usuario);
        Map<String, Object> order = obrasRepository.findOrderById(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("changed", changed);
        out.put("orderId", orderId);
        out.put(
                "estado",
                BiesseObrasRepository.normalizeEstadoForUi(String.valueOf(order.get("estado_escaneo"))));
        out.put("orderName", order.get("ordername"));
        out.put("bookingCode", order.get("bookingcode"));
        return ResponseEntity.ok(out);
    }

    @PostMapping("/orders/entregado")
    public ResponseEntity<Map<String, Object>> markEntregadoByRef(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = BiesseInternalAuth.HEADER_INTERNAL, required = false) String internalToken,
            @RequestBody(required = false) Map<String, Object> body) {
        internalAuth.requireWrite(authorization, internalToken);
        schemaAligner.ensureReady();
        String orderName = body == null ? null : str(body.get("orderName"));
        if (orderName == null) {
            orderName = body == null ? null : str(body.get("ordername"));
        }
        String bookingCode = body == null ? null : str(body.get("bookingCode"));
        if (bookingCode == null) {
            bookingCode = body == null ? null : str(body.get("bookingcode"));
        }
        String usuario = body == null ? null : str(body.get("usuario"));
        Map<String, Object> order = obrasRepository.findOrderByNameOrBooking(orderName, bookingCode);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra no encontrada");
        }
        long orderId = ((Number) order.get("orderid")).longValue();
        boolean changed = obrasRepository.markOrderEntregado(orderId, usuario);
        Map<String, Object> refreshed = obrasRepository.findOrderById(orderId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("changed", changed);
        out.put("orderId", orderId);
        out.put(
                "estado",
                BiesseObrasRepository.normalizeEstadoForUi(
                        refreshed == null ? null : String.valueOf(refreshed.get("estado_escaneo"))));
        out.put("orderName", order.get("ordername"));
        out.put("bookingCode", order.get("bookingcode"));
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

    public record MarkCortadaRequest(
            Long orderId, String osiPart, Long partId, Integer pieceNumber, String machineName) {}

    @GetMapping("/parts/for-osi")
    public ResponseEntity<Map<String, Object>> partForOsi(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = BiesseInternalAuth.HEADER_INTERNAL, required = false) String internalToken,
            @RequestParam long orderId,
            @RequestParam String osiPart,
            @RequestParam(required = false) String machineName,
            @RequestParam(defaultValue = "true") boolean markCortada) {
        internalAuth.requireWrite(authorization, internalToken);
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
            out.put("pieceNumber", null);
            out.put("partCode", osiPart);
            out.put("material", null);
            out.put("cortada", false);
            out.put(
                    "zpl",
                    SimpleZplBuilder.build(
                            SimpleZplBuilder.LabelData.builder()
                                    .orderName(orderName)
                                    .bookingCode(booking)
                                    .partCode(osiPart)
                                    .osiPart(osiPart)
                                    .unitCode(unitCode)
                                    .machineName(machineName)
                                    .build()));
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
        String material = str(part.get("material"));
        String partCode = str(part.get("partcode"));
        String desc1 = str(part.get("descripcion"));
        String desc2 = str(part.get("descripcion1"));
        double length = toDouble(part.get("longitud"));
        double width = toDouble(part.get("ancho"));
        int qty = intOrZero(part.get("cantidad"));

        Map<String, Object> cortadaInfo = null;
        if (markCortada) {
            obrasRepository.ensurePiezaRow(partId, pieceNum);
            cortadaInfo = obrasRepository.markPiezaCortada(partId, pieceNum, machineName);
        }

        out.put("mapStatus", "MAPPED");
        out.put("unitCode", unitCode);
        out.put("partId", partId);
        out.put("pieceNumber", pieceNum);
        out.put("partCode", partCode);
        out.put("material", material);
        out.put("length", length > 0 ? length : null);
        out.put("width", width > 0 ? width : null);
        out.put("cortada", cortadaInfo != null);
        out.put("cortadaInfo", cortadaInfo);
        out.put(
                "zpl",
                SimpleZplBuilder.build(
                        SimpleZplBuilder.LabelData.builder()
                                .orderName(orderName)
                                .bookingCode(booking)
                                .partCode(partCode)
                                .material(material)
                                .unitCode(unitCode)
                                .machineName(machineName)
                                .desc1(desc1)
                                .desc2(desc2)
                                .osiPart(osiPart)
                                .partLabel("P" + partNumber)
                                .length(length)
                                .width(width)
                                .partNumber(partNumber)
                                .pieceNumber(pieceNum)
                                .quantity(qty)
                                .build()));
        return ResponseEntity.ok(out);
    }

    /** Marca pieza cortada; crea la fila de esa pieza si aún no existe (solo al cortar). */
    @PostMapping("/parts/mark-cortada")
    public ResponseEntity<Map<String, Object>> markCortada(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = BiesseInternalAuth.HEADER_INTERNAL, required = false) String internalToken,
            @RequestBody MarkCortadaRequest body) {
        internalAuth.requireWrite(authorization, internalToken);
        schemaAligner.ensureReady();
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body requerido");
        }

        Long partId = body.partId();
        Integer pieceNumber = body.pieceNumber();
        if (partId == null && body.orderId() != null && body.osiPart() != null) {
            Map<String, Object> part = obrasRepository.findPartForOsi(body.orderId(), body.osiPart());
            if (part != null) {
                partId = ((Number) part.get("partid")).longValue();
                if (pieceNumber == null) {
                    pieceNumber = obrasRepository.nextPieceNumber(partId);
                }
            }
        }
        if (partId == null || pieceNumber == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "partId+pieceNumber u orderId+osiPart requeridos");
        }
        obrasRepository.ensurePiezaRow(partId, pieceNumber);
        Map<String, Object> result =
                obrasRepository.markPiezaCortada(partId, pieceNumber, body.machineName());
        if (result == null) {
            Map<String, Object> missing = new LinkedHashMap<>();
            missing.put("found", false);
            missing.put("partId", partId);
            missing.put("pieceNumber", pieceNumber);
            return ResponseEntity.ok(missing);
        }
        Map<String, Object> out = new LinkedHashMap<>(result);
        out.put("found", true);
        return ResponseEntity.ok(out);
    }

    /** Backfill: monitor del agente → {@code piezas.cortada} para una orden (idempotente). */
    @PostMapping("/orders/{orderId}/sync-agent-cuts")
    public ResponseEntity<Map<String, Object>> syncAgentCuts(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = BiesseInternalAuth.HEADER_INTERNAL, required = false) String internalToken,
            @PathVariable long orderId) {
        internalAuth.requireWrite(authorization, internalToken);
        schemaAligner.ensureReady();
        if (obrasRepository.findOrderById(orderId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada");
        }
        return ResponseEntity.ok(agentCutSyncService.syncOrderFromMonitor(orderId));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int intOrZero(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static int numberInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o instanceof String s) {
            String t = s.trim().replace(',', '.');
            if (t.isEmpty()) {
                return 0;
            }
            try {
                return Double.parseDouble(t);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
