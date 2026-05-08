package com.allcenter.modulebiesse.service;

import com.allcenter.modulebiesse.dto.PendingPartResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
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
            throw new ResponseStatusException(NOT_FOUND, "Part not found");
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
                "Parte " + part.get("partcode") + " escaneada. Programada=" + scheduledQuantity + ", Escaneada="
                        + req.scannedQuantity() + ", Diferencia=" + difference + ", Metodo=" + method,
                method,
                req.equipment());
        repository.completeOrderIfNeeded(orderId, employeeId);

        return new ScanResultResponse(true, "Part scanned successfully with method " + method);
    }

    @Transactional
    public ScanResultResponse scanPiece(Long employeeId, ScanPieceRequest req) {
        boolean ok = repository.scanPiece(employeeId, req.pieceId(), req.observations(), req.equipment());
        if (!ok) {
            throw new ResponseStatusException(BAD_REQUEST, "Piece not found or already scanned");
        }
        return new ScanResultResponse(true, "Piece scanned successfully");
    }

    public UserScanStatsResponse getMyStats(Long employeeId) {
        return repository.getUserStats(employeeId);
    }

    public List<Map<String, Object>> getMyScannedParts(
            Long employeeId, String fromDate, String toDate, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return repository.findScannedPartsByUser(employeeId, fromDate, toDate, safeLimit);
    }

    public List<Map<String, Object>> getOrders(String state, String query, int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        int safeOffset = Math.max(0, offset);
        return repository.findOrders(state, query, safeLimit, safeOffset);
    }

    public Map<String, Object> getOrderDetail(Long orderId) {
        Map<String, Object> order = repository.findOrderById(orderId);
        if (order == null) {
            throw new ResponseStatusException(NOT_FOUND, "Order not found");
        }
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
        String value = code.trim();
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("^(.*)-P?(\\d+)-(\\d+)$", java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(value);
        if (!matcher.matches()) {
            throw new ResponseStatusException(BAD_REQUEST, "Code must follow ordername-Px-y");
        }
        String orderName = matcher.group(1).trim();
        String partToken = matcher.group(2);
        String pieceToken = matcher.group(3);
        Map<String, Object> piece = repository.resolvePieceByCompositeCode(orderName, partToken, pieceToken);
        if (piece == null) {
            throw new ResponseStatusException(NOT_FOUND, "Piece not found for provided code");
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
}
