package com.allcenter.modulebiesse.service;

import com.allcenter.modulebiesse.dto.PendingPartResponse;
import com.allcenter.modulebiesse.dto.ScanInterpretResponse;
import com.allcenter.modulebiesse.dto.ScanPartRequest;
import com.allcenter.modulebiesse.dto.ScanPieceRequest;
import com.allcenter.modulebiesse.dto.ScanResultResponse;
import com.allcenter.modulebiesse.dto.UserScanStatsResponse;
import java.util.HashMap;
import com.allcenter.modulebiesse.repository.BiesseScanRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class BiesseScanService {

    private final BiesseScanRepository repository;

    public List<PendingPartResponse> getPendingParts(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return repository.findPendingParts(safeLimit);
    }

    @Transactional
    public ScanResultResponse scanPart(Long employeeId, ScanPartRequest req) {
        Map<String, Object> part = repository.findPartById(req.partId());
        if (part == null) {
            throw new ResponseStatusException(NOT_FOUND, "Parte no encontrada");
        }

        String method = normalizeMethod(req.method());
        boolean alreadyScanned = (boolean) part.get("escaneado");
        if (alreadyScanned && !"CORRECCION".equals(method)) {
            throw new ResponseStatusException(BAD_REQUEST, "Part was already scanned");
        }

        int scheduledQuantity = (int) part.get("cantidad");
        int difference = req.scannedQuantity() - scheduledQuantity;
        int updated = repository.updatePartScan(employeeId, req, difference, method);
        if (updated == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Part scan update failed");
        }

        Long orderId = ((Number) part.get("orderid")).longValue();
        repository.insertScanAudit(
                employeeId,
                orderId,
                req.partId(),
                "ESCANEAR",
                "Parte "
                        + part.get("partcode")
                        + ": Método="
                        + method
                        + ", Programada="
                        + scheduledQuantity
                        + ", Escaneada="
                        + req.scannedQuantity()
                        + ", Diferencia="
                        + difference
                        + ", Observaciones: "
                        + (req.observations() != null && !req.observations().isBlank()
                                ? req.observations()
                                : "Ninguna"),
                method,
                req.equipment() != null ? req.equipment() : "",
                req.scanTimeMs());
        repository.syncOrderScanProgress(orderId);
        repository.completeOrderIfNeeded(orderId, employeeId);

        return new ScanResultResponse(true, "Part scanned successfully with method " + method);
    }

    @Transactional
    public ScanResultResponse scanPiece(Long employeeId, ScanPieceRequest req) {
        Long pieceId = req.pieceId();
        boolean fromPallet = isPalletEquipment(req.equipment());
        BiesseScanRepository.PieceScanState state = repository.getPieceScanState(pieceId);
        if (state == null) {
            throw new ResponseStatusException(NOT_FOUND, "Pieza no reconocida");
        }
        if (!fromPallet) {
            Map<String, Object> paleAssignment = repository.findPaleAssignmentByPieceId(pieceId);
            if (paleAssignment != null) {
                String paleCode = String.valueOf(paleAssignment.getOrDefault("codigo", "—"));
                throw new ResponseStatusException(
                        CONFLICT, "La pieza ya está en el palé " + paleCode);
            }
            if (state.scanned()) {
                throw new ResponseStatusException(BAD_REQUEST, "La pieza ya fue escaneada");
            }
        } else if (state.scanned()) {
            return new ScanResultResponse(true, "Pieza ya estaba escaneada");
        }
        boolean ok = repository.scanPiece(employeeId, pieceId, req.observations(), req.equipment());
        if (!ok) {
            throw new ResponseStatusException(BAD_REQUEST, "No se pudo registrar el escaneo de la pieza");
        }
        return new ScanResultResponse(true, "Pieza escaneada correctamente");
    }

    public record AndroidScanNotify(String orderName, String bookingCode, boolean complete) {}

    public AndroidScanNotify progressForPiece(Long pieceId) {
        Map<String, Object> piece = repository.findPieceById(pieceId);
        if (piece == null || piece.get("orderid") == null) {
            return null;
        }
        return progressForOrder(((Number) piece.get("orderid")).longValue());
    }

    public AndroidScanNotify progressForPart(Long partId) {
        Map<String, Object> part = repository.findPartById(partId);
        if (part == null || part.get("orderid") == null) {
            return null;
        }
        return progressForOrder(((Number) part.get("orderid")).longValue());
    }

    public AndroidScanNotify progressForOrder(Long orderId) {
        Map<String, Object> order = repository.findOrderById(orderId);
        if (order == null) {
            return null;
        }
        return new AndroidScanNotify(
                stringify(order.get("ordername")),
                stringify(order.get("bookingcode")),
                repository.isOrderScanComplete(orderId));
    }

    private static String stringify(Object v) {
        if (v == null) {
            return null;
        }
        String t = String.valueOf(v).trim();
        return t.isEmpty() || "null".equalsIgnoreCase(t) ? null : t;
    }

    @Transactional
    public ScanResultResponse unscanPiece(Long employeeId, ScanPieceRequest req) {
        boolean ok = repository.unscanPiece(employeeId, req.pieceId(), req.observations(), req.equipment());
        if (!ok) {
            // Idempotente para pale: si ya estaba libre, el pale puede quitar la línea igual.
            if (req.equipment() != null && "PALLET".equalsIgnoreCase(req.equipment().trim())) {
                return new ScanResultResponse(true, "Pieza ya estaba libre para escaneo");
            }
            throw new ResponseStatusException(BAD_REQUEST, "Piece not found or not scanned");
        }
        return new ScanResultResponse(true, "Piece unscan successful");
    }

    public UserScanStatsResponse getMyStats(Long employeeId) {
        return repository.getUserStats(employeeId);
    }

    public List<Map<String, Object>> getMyScannedParts(
            Long employeeId, String fromDate, String toDate, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return repository.findScannedPartsByUser(employeeId, fromDate, toDate, safeLimit);
    }

    public List<Map<String, Object>> getOrders(
            Long orderId, String state, String query, String fromDate, String toDate, int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        int safeOffset = Math.max(0, offset);
        return repository.findOrders(orderId, state, query, fromDate, toDate, safeLimit, safeOffset);
    }

    public List<Map<String, Object>> getAudit(
            Long orderId, Long partId, String orderQ, String partQ, String action, int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        int safeOffset = Math.max(0, offset);
        return repository.findScanAudit(orderId, partId, orderQ, partQ, action, safeLimit, safeOffset);
    }

    public Map<String, Object> getOrderDetail(Long orderId) {
        Map<String, Object> order = repository.findOrderById(orderId);
        if (order == null) {
            throw new ResponseStatusException(NOT_FOUND, "Order not found");
        }
        repository.syncOrderScanProgress(orderId);
        order = repository.findOrderById(orderId);
        List<Map<String, Object>> parts = repository.findOrderParts(orderId);
        List<Map<String, Object>> pieces = repository.findOrderPieces(orderId);

        Map<Long, List<Map<String, Object>>> piecesByPart = new LinkedHashMap<>();
        for (Map<String, Object> piece : pieces) {
            Long partId = ((Number) piece.get("partid")).longValue();
            piecesByPart.computeIfAbsent(partId, key -> new ArrayList<>()).add(piece);
        }
        for (Map<String, Object> part : parts) {
            Long partId = ((Number) part.get("partid")).longValue();
            part.put("piezas", piecesByPart.getOrDefault(partId, List.of()));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("order", order);
        response.put("part_stats", repository.findOrderPartStats(orderId));
        response.put("parts", parts);
        return response;
    }

    /**
     * Interpreta un código de barras como la app Android ({@code ScanInterpretRequest} /
     * {@code ScanInterpretResponse}).
     */
    public ScanInterpretResponse interpretScan(String rawCode, Long currentOrderId, Boolean confirmOrderSwitch) {
        String normalized = normalizeBarcodeInput(rawCode);
        if (normalized.length() < 2) {
            return interpretError("Código vacío");
        }
        boolean confirm = Boolean.TRUE.equals(confirmOrderSwitch);

        Matcher composite =
                Pattern.compile("^(.*)-P?(\\d+)-(\\d+)$", Pattern.CASE_INSENSITIVE).matcher(normalized);
        if (composite.matches()) {
            String partToken = composite.group(2).trim();
            int pieceNumber = Integer.parseInt(composite.group(3).trim());
            Map<String, Object> piece = null;
            if (currentOrderId != null) {
                piece = repository.findPieceByOrderPartAndNumber(currentOrderId, partToken, pieceNumber);
            }
            if (piece == null) {
                piece = repository.resolvePieceFromScanCode(normalized);
            }
            if (piece == null) {
                return interpretError("Pieza no reconocida para el código: " + normalized);
            }
            ScanInterpretResponse paleError = pieceOnPaleError(piece);
            if (paleError != null) {
                return paleError;
            }
            Long orderId = ((Number) piece.get("orderid")).longValue();
            ScanInterpretResponse switchCheck = checkOrderSwitch(currentOrderId, orderId, confirm, piece);
            if (switchCheck != null) {
                return switchCheck;
            }
            return scanPieceResponse(piece, "Pieza identificada");
        }

        Matcher partOnly = Pattern.compile("^(.*)-P?(\\d+)$", Pattern.CASE_INSENSITIVE).matcher(normalized);
        if (partOnly.matches()) {
            String orderToken = partOnly.group(1).trim();
            String partToken = partOnly.group(2);
            Map<String, Object> order = repository.findOrderByNameToken(orderToken);
            if (order != null) {
                Long orderId = ((Number) order.get("orderid")).longValue();
                ScanInterpretResponse switchCheck = checkOrderSwitch(currentOrderId, orderId, confirm, order);
                if (switchCheck != null) {
                    return switchCheck;
                }
                Map<String, Object> part = repository.findPartByOrderAndToken(orderId, partToken);
                if (part != null) {
                    return scanPartResponse(part, order, "Parte identificada");
                }
            }
        }

        Long orderId = currentOrderId != null ? currentOrderId : repository.detectOrderIdFromCode(normalized);
        if (orderId == null) {
            return interpretError("No se pudo identificar la orden desde el código");
        }

        Map<String, Object> order = repository.findOrderById(orderId);
        if (order == null) {
            return interpretError("No se pudo cargar detalle de la orden");
        }

        ScanInterpretResponse switchCheck = checkOrderSwitch(currentOrderId, orderId, confirm, order);
        if (switchCheck != null) {
            return switchCheck;
        }

        if (isOrderLabelOnly(normalized, order)) {
            return loadOrderResponse(order, "Orden cargada");
        }

        ScanInterpretResponse withinOrder = interpretWithinOrder(orderId, normalized, order);
        if (withinOrder != null) {
            return withinOrder;
        }

        return interpretError("No se encontró parte válida en el código");
    }

    @Transactional
    public Map<String, Object> updateOrder(Long employeeId, Long orderId, String observaciones) {
        Map<String, Object> order = repository.findOrderById(orderId);
        if (order == null) {
            throw new ResponseStatusException(NOT_FOUND, "Order not found");
        }
        int updated = repository.updateOrderObservaciones(orderId, observaciones == null ? "" : observaciones.trim());
        if (updated == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Order update failed");
        }
        repository.insertScanAudit(
                employeeId,
                orderId,
                null,
                "EDITAR_ORDEN",
                "Observaciones de orden actualizadas",
                "MANUAL",
                "FRONTEND");
        return repository.findOrderById(orderId);
    }

    @Transactional
    public void deleteOrder(Long employeeId, Long orderId) {
        Map<String, Object> order = repository.findOrderById(orderId);
        if (order == null) {
            throw new ResponseStatusException(NOT_FOUND, "Order not found");
        }
        int paleLines = repository.countPaleDetailsByOrderId(orderId);
        if (paleLines > 0) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "La orden figura en "
                            + paleLines
                            + " línea(s) de palé. Quítela de los palés antes de eliminar la orden.");
        }
        String orderName = String.valueOf(order.getOrDefault("ordername", orderId));
        boolean deleted = repository.deleteOrderById(orderId);
        if (!deleted) {
            throw new ResponseStatusException(BAD_REQUEST, "Order delete failed");
        }
        repository.insertScanAudit(
                employeeId,
                null,
                null,
                "ELIMINAR_ORDEN",
                "Orden " + orderId + " (" + orderName + ") eliminada manualmente",
                "MANUAL",
                "FRONTEND");
    }

    @Transactional
    public ScanResultResponse completeOrder(Long employeeId, Long orderId, String method) {
        String normalized = normalizeCompleteMethod(method);
        boolean ok = repository.completeOrderManual(orderId, employeeId, normalized);
        if (!ok) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Order cannot be completed because it has pending parts");
        }
        return new ScanResultResponse(true, "Order completed with method " + normalized);
    }

    public Map<String, Object> getGeneralStats() {
        return repository.getGeneralStats();
    }

    public Map<String, Object> resolvePieceFromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Code is required");
        }
        String value = normalizeBarcodeInput(code);
        if (!Pattern.compile("^(.*)-P?(\\d+)-(\\d+)$", Pattern.CASE_INSENSITIVE).matcher(value).matches()) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "El código debe tener el formato Orden-P#-N (ej. MIORDEN-P12-3)");
        }
        Map<String, Object> piece = repository.resolvePieceFromScanCode(value);
        if (piece == null) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "Pieza no reconocida para el código indicado. "
                            + "Formato esperado: Orden-P#-N (ej. MIORDEN-P12-3).");
        }
        Map<String, Object> paleAssignment =
                repository.findPaleAssignmentByPieceId(((Number) piece.get("piezaid")).longValue());
        if (paleAssignment != null) {
            String paleCode = String.valueOf(paleAssignment.getOrDefault("codigo", "—"));
            throw new ResponseStatusException(CONFLICT, "La pieza ya está en el palé " + paleCode);
        }
        String medida = formatMedidaPair(toDouble(piece.get("longitud_parte")), toDouble(piece.get("ancho_parte")));
        if (medida != null) {
            piece.put("medida", medida);
        }
        return piece;
    }

    public Map<String, Object> getPieceById(Long pieceId) {
        Map<String, Object> piece = repository.findPieceById(pieceId);
        if (piece == null) {
            throw new ResponseStatusException(NOT_FOUND, "Piece not found");
        }
        String medida = formatMedidaPair(toDouble(piece.get("longitud_parte")), toDouble(piece.get("ancho_parte")));
        if (medida != null) {
            piece.put("medida", medida);
        }
        return piece;
    }

    private static Double toDouble(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            String t = s.trim().replace(',', '.');
            if (t.isEmpty()) {
                return null;
            }
            try {
                return Double.parseDouble(t);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String formatMedidaPair(Double longitud, Double ancho) {
        if (longitud == null && ancho == null) {
            return null;
        }
        String l = longitud == null ? "—" : trimDecimal(longitud);
        String a = ancho == null ? "—" : trimDecimal(ancho);
        return l + " × " + a;
    }

    private static String trimDecimal(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "—";
        }
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    private String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return "AUTOMATICO";
        }
        String value = method.trim().toUpperCase(Locale.ROOT);
        if ("AUTOMATICO".equals(value) || "MANUAL".equals(value) || "CORRECCION".equals(value)) {
            return value;
        }
        return "AUTOMATICO";
    }

    private String normalizeCompleteMethod(String method) {
        if (method == null || method.isBlank()) {
            return "MANUAL";
        }
        String value = method.trim().toUpperCase(Locale.ROOT);
        if ("MANUAL".equals(value) || "AUTOMATICA".equals(value) || "AUTOMATICA".equals(value)) {
            return value;
        }
        return "MANUAL";
    }

    private static boolean isPalletEquipment(String equipment) {
        return equipment != null && "PALLET".equalsIgnoreCase(equipment.trim());
    }

    private static String normalizeBarcodeInput(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        StringBuilder out = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            switch (c) {
                case '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2212', '\uFE63', '\uFF0D' -> out.append('-');
                default -> {
                    if (!Character.isISOControl(c) || c == '\t') {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString().trim();
    }

    private ScanInterpretResponse checkOrderSwitch(
            Long currentOrderId, Long targetOrderId, boolean confirm, Map<String, Object> context) {
        if (currentOrderId == null || currentOrderId.equals(targetOrderId) || confirm) {
            return null;
        }
        String orderName = String.valueOf(context.getOrDefault("ordername", ""));
        return new ScanInterpretResponse(
                "ORDER_SWITCH_REQUIRED",
                targetOrderId,
                orderName,
                null,
                null,
                null,
                null,
                null,
                true,
                targetOrderId,
                "El código corresponde a otra orden");
    }

    private static boolean isOrderLabelOnly(String normalized, Map<String, Object> order) {
        String upper = normalized.toUpperCase(Locale.ROOT);
        String orderName = String.valueOf(order.getOrDefault("ordername", "")).toUpperCase(Locale.ROOT);
        Object booking = order.get("bookingcode");
        String bookingCode = booking != null ? String.valueOf(booking).toUpperCase(Locale.ROOT) : "";
        return upper.equals(orderName) || (!bookingCode.isBlank() && upper.equals(bookingCode));
    }

    private ScanInterpretResponse interpretWithinOrder(
            Long orderId, String normalized, Map<String, Object> order) {
        String upper = normalized.toUpperCase(Locale.ROOT);

        Matcher suffixPiece =
                Pattern.compile("-P?(\\d+)-(\\d+)$", Pattern.CASE_INSENSITIVE).matcher(normalized);
        if (suffixPiece.find()) {
            String partToken = suffixPiece.group(1);
            int pieceNumber = Integer.parseInt(suffixPiece.group(2));
            Map<String, Object> piece = repository.findPieceByOrderPartAndNumber(orderId, partToken, pieceNumber);
            if (piece != null) {
                return validatedScanPieceResponse(piece, "Pieza identificada");
            }
            Map<String, Object> part = repository.findPartByOrderAndToken(orderId, partToken);
            if (part != null) {
                return scanPartResponse(part, order, "Parte identificada");
            }
            return interpretError("Pieza no reconocida para el código: " + normalized);
        }

        List<Map<String, Object>> parts = repository.findOrderParts(orderId);
        for (Map<String, Object> part : parts) {
            Object partCodeObj = part.get("partcode");
            if (partCodeObj == null) {
                continue;
            }
            String partCode = String.valueOf(partCodeObj).toUpperCase(Locale.ROOT);
            if (partCode.isBlank() || !upper.contains(partCode)) {
                continue;
            }
            Matcher trailing = Pattern.compile("-(\\d+)$").matcher(upper);
            if (trailing.find()) {
                int pieceNumber = Integer.parseInt(trailing.group(1));
                Map<String, Object> piece =
                        repository.findPieceByOrderPartAndNumber(
                                orderId, String.valueOf(part.get("partnumber")), pieceNumber);
                if (piece == null && partCode.startsWith("P")) {
                    piece =
                            repository.findPieceByOrderPartAndNumber(
                                    orderId, partCode.substring(1), pieceNumber);
                }
                if (piece != null) {
                    return validatedScanPieceResponse(piece, "Pieza identificada");
                }
            }
            return scanPartResponse(part, order, "Parte identificada");
        }
        return null;
    }

    private ScanInterpretResponse validatedScanPieceResponse(Map<String, Object> piece, String message) {
        ScanInterpretResponse paleError = pieceOnPaleError(piece);
        if (paleError != null) {
            return paleError;
        }
        return scanPieceResponse(piece, message);
    }

    private ScanInterpretResponse pieceOnPaleError(Map<String, Object> piece) {
        Object idObj = piece.get("piezaid");
        if (idObj == null) {
            return null;
        }
        Long piezaId = ((Number) idObj).longValue();
        Map<String, Object> pale = repository.findPaleAssignmentByPieceId(piezaId);
        if (pale == null) {
            return null;
        }
        String codigo = String.valueOf(pale.getOrDefault("codigo", "—"));
        return interpretError("La pieza ya está en el palé " + codigo);
    }

    private static ScanInterpretResponse loadOrderResponse(Map<String, Object> order, String message) {
        return new ScanInterpretResponse(
                "LOAD_ORDER",
                ((Number) order.get("orderid")).longValue(),
                String.valueOf(order.get("ordername")),
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                message);
    }

    private static ScanInterpretResponse scanPartResponse(
            Map<String, Object> part, Map<String, Object> order, String message) {
        return new ScanInterpretResponse(
                "SCAN_PART",
                ((Number) order.get("orderid")).longValue(),
                String.valueOf(order.get("ordername")),
                part.get("partcode") != null ? String.valueOf(part.get("partcode")) : null,
                ((Number) part.get("partid")).longValue(),
                null,
                null,
                1,
                false,
                null,
                message);
    }

    private static ScanInterpretResponse scanPieceResponse(Map<String, Object> piece, String message) {
        return new ScanInterpretResponse(
                "SCAN_PIECE",
                ((Number) piece.get("orderid")).longValue(),
                piece.get("ordername") != null ? String.valueOf(piece.get("ordername")) : null,
                piece.get("partcode") != null ? String.valueOf(piece.get("partcode")) : null,
                ((Number) piece.get("partid")).longValue(),
                piece.get("numero_pieza") != null ? ((Number) piece.get("numero_pieza")).intValue() : null,
                ((Number) piece.get("piezaid")).longValue(),
                null,
                false,
                null,
                message);
    }

    private static ScanInterpretResponse interpretError(String message) {
        return new ScanInterpretResponse(
                "ERROR", null, null, null, null, null, null, null, false, null, message);
    }
}
