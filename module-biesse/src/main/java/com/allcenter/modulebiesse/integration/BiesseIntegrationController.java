package com.allcenter.modulebiesse.integration;

import com.allcenter.modulebiesse.obras.BiesseObrasRepository;
import com.allcenter.modulebiesse.obras.BiesseObrasSchemaAligner;
import com.allcenter.modulebiesse.repository.BiesseScanRepository;
import com.allcenter.modulebiesse.service.AgentCutSyncService;
import com.allcenter.modulebiesse.service.BiesseScanService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        BiesseObrasRepository.OrderJobMatch match = obrasRepository.resolveOrderForJob(jobName);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ambiguous", match.ambiguous());
        out.put("order", match.order());
        out.put(
                "candidates",
                match.candidates().stream()
                        .map(
                                row -> {
                                    Map<String, Object> c = new LinkedHashMap<>();
                                    c.put("orderId", row.get("orderid"));
                                    c.put("orderName", row.get("ordername"));
                                    c.put("opCodigo", row.get("op_codigo"));
                                    return c;
                                })
                        .toList());
        if (match.order() == null && !match.ambiguous()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada para job");
        }
        return ResponseEntity.ok(out);
    }

    /**
     * Manifiesto de obra para impresión local del agente (mapeo OSI P10 → ERP P1, cantidades, textos).
     */
    @GetMapping("/orders/manifest")
    public ResponseEntity<Map<String, Object>> orderManifest(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = BiesseInternalAuth.HEADER_INTERNAL, required = false) String internalToken,
            @RequestParam String jobName) {
        internalAuth.requireRead(authorization, internalToken);
        schemaAligner.ensureReady();
        BiesseObrasRepository.OrderJobMatch match = obrasRepository.resolveOrderForJob(jobName);
        if (match.ambiguous()) {
            Map<String, Object> conflict = new LinkedHashMap<>();
            conflict.put("ambiguous", true);
            conflict.put(
                    "candidates",
                    match.candidates().stream()
                            .map(
                                    row -> {
                                        Map<String, Object> c = new LinkedHashMap<>();
                                        c.put("orderId", row.get("orderid"));
                                        c.put("orderName", row.get("ordername"));
                                        c.put("opCodigo", row.get("op_codigo"));
                                        return c;
                                    })
                            .toList());
            conflict.put("message", "Varias obras coinciden con el job OSI — corrija el nombre en ERP o en OSI.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(conflict);
        }
        Map<String, Object> order = match.order();
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada para job");
        }
        long orderId = ((Number) order.get("orderid")).longValue();
        String orderName = str(order.get("ordername"));
        String booking = str(order.get("bookingcode"));
        List<Map<String, Object>> parts = scanRepository.findOrderParts(orderId);
        List<Map<String, Object>> manifestParts = new ArrayList<>();
        for (Map<String, Object> part : parts) {
            if (!(part.get("partid") instanceof Number partIdNum)) {
                continue;
            }
            int partNumber = intOrZero(part.get("partnumber"));
            if (partNumber <= 0) {
                continue;
            }
            String partCode = str(part.get("partcode"));
            List<String> osiKeys = new ArrayList<>();
            if (partCode != null && !partCode.isBlank()) {
                String pc = partCode.trim().toUpperCase(Locale.ROOT);
                osiKeys.add(pc);
                if (pc.startsWith("P") && pc.length() > 1) {
                    osiKeys.add(pc.substring(1));
                } else if (!pc.isEmpty()) {
                    osiKeys.add("P" + pc);
                }
            }
            osiKeys.add("P" + partNumber);
            osiKeys.add(String.valueOf(partNumber));
            Map<String, Object> row = new LinkedHashMap<>();
            long partId = partIdNum.longValue();
            row.put("partId", partId);
            row.put("partNumber", partNumber);
            row.put("partCode", partCode);
            row.put("osiKeys", osiKeys.stream().distinct().toList());
            row.put("cantidad", intOrZero(part.get("cantidad")));
            row.put("material", str(part.get("material")));
            row.put("descripcion", str(part.get("descripcion")));
            row.put("descripcion1", str(part.get("descripcion1")));
            row.put("longitud", toDouble(part.get("longitud")));
            row.put("ancho", toDouble(part.get("ancho")));
            row.put("edgeUp", str(part.get("matedgeup")));
            row.put("edgeLo", str(part.get("matedgelo")));
            row.put("edgeL", str(part.get("matedgel")));
            row.put("edgeR", str(part.get("matedger")));
            row.put("cortadasMax", scanRepository.maxCortadaPieceNumber(partId));
            manifestParts.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderId", orderId);
        out.put("orderName", orderName);
        out.put("bookingCode", booking);
        out.put("jobName", jobName.trim());
        out.put("parts", manifestParts);
        return ResponseEntity.ok(out);
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
        double pctSum = 0;
        int piezasTot = 0;
        int piezasEsc = 0;
        int partesTot = 0;
        int partesEsc = 0;
        for (Map<String, Object> o : obras) {
            pctSum += toDouble(o.get("porcentaje"));
            piezasTot += numberInt(o.get("piezas_totales"));
            piezasEsc += numberInt(o.get("piezas_escaneadas"));
            partesTot += numberInt(o.get("total_partes"));
            partesEsc += numberInt(o.get("partes_escaneadas"));
        }
        double pct;
        String avance;
        if (!obras.isEmpty()) {
            pct = Math.round((pctSum / obras.size()) * 10.0) / 10.0;
            if (piezasTot > 0) {
                avance = piezasEsc + "/" + piezasTot + " piezas";
            } else if (partesTot > 0) {
                avance = partesEsc + "/" + partesTot + " partes";
            } else {
                avance = "0/0";
            }
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
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "false") boolean soloCorte) {
        internalAuth.requireRead(authorization, internalToken);
        schemaAligner.ensureReady();
        return ResponseEntity.ok(obrasRepository.listTrazabilidad(op, orderId, limit, soloCorte));
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
            @RequestParam(required = false) Integer pieceNumber,
            @RequestParam(required = false) String unitCode,
            @RequestParam(defaultValue = "true") boolean markCortada) {
        internalAuth.requireWrite(authorization, internalToken);
        schemaAligner.ensureReady();

        Map<String, Object> order = obrasRepository.findOrderById(orderId);
        String orderName = order != null ? str(order.get("ordername")) : ("#" + orderId);
        String booking = order != null ? str(order.get("bookingcode")) : null;
        Map<String, Object> part = obrasRepository.findPartForOsi(orderId, osiPart);

        Map<String, Object> out = new LinkedHashMap<>();
        if (part == null) {
            String fallbackUnitCode =
                    unitCode != null && !unitCode.isBlank()
                            ? unitCode.trim()
                            : orderName + "-" + osiPart.replaceAll("\\s+", "");
            out.put("mapStatus", "UNMAPPED");
            out.put("unitCode", fallbackUnitCode);
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
                                    .unitCode(fallbackUnitCode)
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
        int qty = intOrZero(part.get("cantidad"));
        Integer resolvedPiece = null;
        boolean allowRecorte = false;
        if (pieceNumber != null && pieceNumber > 0) {
            resolvedPiece = pieceNumber;
            // Si el agente repite el mismo N ya cortado → recorte (morado).
            allowRecorte = true;
        } else if (markCortada) {
            Integer recent = obrasRepository.lastCortadaPieceIfWithinSeconds(partId, 2);
            if (recent != null) {
                // Evento duplicado casi inmediato: misma pieza, no avanzar 1..N.
                resolvedPiece = recent;
                allowRecorte = true;
            } else {
                Integer next = obrasRepository.nextPieceNumber(partId);
                if (next != null) {
                    resolvedPiece = next;
                } else if (qty > 0) {
                    resolvedPiece = qty;
                    allowRecorte = true;
                }
            }
        }
        int pieceNum = resolvedPiece != null ? resolvedPiece : 0;

        String material = str(part.get("material"));
        String partCode = str(part.get("partcode"));
        String desc1 = str(part.get("descripcion"));
        String desc2 = str(part.get("descripcion1"));
        double length = toDouble(part.get("longitud"));
        double width = toDouble(part.get("ancho"));

        Map<String, Object> cortadaInfo = null;
        Map<String, Object> errorInfo = null;
        if (markCortada && pieceNum > 0) {
            if (qty > 0 && pieceNum > qty) {
                // Fuera de plan: error visual en la última pieza válida (no inventa filas).
                errorInfo =
                        obrasRepository.markPiezaCorteError(
                                partId,
                                qty,
                                "Captura fuera de cantidad del plan (" + pieceNum + ">" + qty + ")");
            } else {
                obrasRepository.ensurePiezaRow(partId, pieceNum);
                cortadaInfo =
                        obrasRepository.markPiezaCortada(partId, pieceNum, machineName, allowRecorte);
                if (cortadaInfo == null) {
                    errorInfo =
                            obrasRepository.markPiezaCorteError(
                                    partId, pieceNum, "No se pudo marcar corte OSI: " + osiPart);
                }
            }
        }

        String resolvedUnitCode =
                unitCode != null && !unitCode.isBlank()
                        ? unitCode.trim()
                        : (pieceNum > 0
                                ? orderName + "-P" + partNumber + "-" + pieceNum
                                : orderName + "-P" + partNumber);
        out.put("mapStatus", "MAPPED");
        out.put("unitCode", resolvedUnitCode);
        out.put("partId", partId);
        out.put("pieceNumber", pieceNum);
        out.put("partCode", partCode);
        out.put("material", material);
        out.put("length", length > 0 ? length : null);
        out.put("width", width > 0 ? width : null);
        out.put("cortada", cortadaInfo != null);
        out.put("cortadaInfo", cortadaInfo);
        out.put("corteError", errorInfo != null);
        out.put("corteErrorInfo", errorInfo);
        out.put(
                "zpl",
                SimpleZplBuilder.build(
                        SimpleZplBuilder.LabelData.builder()
                                .orderName(orderName)
                                .bookingCode(booking)
                                .partCode(partCode)
                                .material(material)
                                .unitCode(resolvedUnitCode)
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

    /**
     * ZPL de etiqueta (fuente única ERP) para impresión local del agente sin marcar cortada.
     */
    @GetMapping("/labels/zpl")
    public ResponseEntity<Map<String, Object>> labelZpl(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = BiesseInternalAuth.HEADER_INTERNAL, required = false) String internalToken,
            @RequestParam String jobName,
            @RequestParam String osiPart,
            @RequestParam int pieceNumber,
            @RequestParam String unitCode,
            @RequestParam(required = false) String machineName) {
        internalAuth.requireRead(authorization, internalToken);
        schemaAligner.ensureReady();
        BiesseObrasRepository.OrderJobMatch match = obrasRepository.resolveOrderForJob(jobName);
        if (match.ambiguous()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Obra ambigua para job «" + jobName + "»");
        }
        Map<String, Object> order = match.order();
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada para job");
        }
        long orderId = ((Number) order.get("orderid")).longValue();
        String orderName = str(order.get("ordername"));
        String booking = str(order.get("bookingcode"));
        Map<String, Object> part = obrasRepository.findPartForOsi(orderId, osiPart);
        Map<String, Object> out = new LinkedHashMap<>();
        if (part == null) {
            out.put("zpl", SimpleZplBuilder.build(
                    SimpleZplBuilder.LabelData.builder()
                            .orderName(orderName)
                            .bookingCode(booking)
                            .partCode(osiPart)
                            .osiPart(osiPart)
                            .unitCode(unitCode)
                            .machineName(machineName)
                            .pieceNumber(pieceNumber)
                            .build()));
            return ResponseEntity.ok(out);
        }
        int partNumber = intOrZero(part.get("partnumber"));
        int qty = intOrZero(part.get("cantidad"));
        String edge =
                EdgeLabelFormatter.format(
                        str(part.get("matedgeup")),
                        str(part.get("matedgelo")),
                        str(part.get("matedgel")),
                        str(part.get("matedger")));
        out.put(
                "zpl",
                SimpleZplBuilder.build(
                        SimpleZplBuilder.LabelData.builder()
                                .orderName(orderName)
                                .bookingCode(booking)
                                .partCode(str(part.get("partcode")))
                                .material(str(part.get("material")))
                                .unitCode(unitCode)
                                .machineName(machineName)
                                .desc1(str(part.get("descripcion")))
                                .desc2(str(part.get("descripcion1")))
                                .edgeLabel(edge)
                                .osiPart(osiPart)
                                .partLabel("P" + partNumber)
                                .length(toDouble(part.get("longitud")))
                                .width(toDouble(part.get("ancho")))
                                .partNumber(partNumber)
                                .pieceNumber(pieceNumber)
                                .quantity(qty > 0 ? qty : 1)
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
                    pieceNumber = obrasRepository.resolvePieceNumberForCut(partId, null);
                }
            }
        }
        if (partId == null || pieceNumber == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "partId+pieceNumber u orderId+osiPart requeridos");
        }
        if (!obrasRepository.ensurePiezaRow(partId, pieceNumber)) {
            Integer qty = obrasRepository.partCantidad(partId);
            if (qty != null && qty > 0) {
                obrasRepository.markPiezaCorteError(
                        partId,
                        qty,
                        "Captura fuera de cantidad (" + pieceNumber + ">" + qty + ")");
            }
            Map<String, Object> missing = new LinkedHashMap<>();
            missing.put("found", false);
            missing.put("partId", partId);
            missing.put("pieceNumber", pieceNumber);
            missing.put("reason", "pieza fuera de cantidad o parte inexistente");
            return ResponseEntity.ok(missing);
        }
        Map<String, Object> result =
                obrasRepository.markPiezaCortada(partId, pieceNumber, body.machineName());
        if (result == null) {
            obrasRepository.markPiezaCorteError(
                    partId, pieceNumber, "No se pudo marcar corte (mark-cortada)");
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
