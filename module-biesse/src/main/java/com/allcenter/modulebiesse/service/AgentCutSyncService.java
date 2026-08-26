package com.allcenter.modulebiesse.service;

import com.allcenter.modulebiesse.obras.BiesseObrasRepository;
import com.allcenter.modulebiesse.repository.BiesseScanRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sincroniza cortes del monitor del agente → {@code piezas.cortada} (idempotente).
 * No modifica {@code escaneado}; independiente del flujo de escaneo Android/palés.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentCutSyncService {

    private static final Pattern UNIT_CODE_SUFFIX =
            Pattern.compile("-P?(\\d+)-(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);

    private final BiesseObrasRepository obrasRepository;
    private final BiesseScanRepository scanRepository;
    private final SystemAgentCutsClient agentCutsClient;

    @Transactional
    public Map<String, Object> syncOrderFromMonitor(long orderId) {
        List<Map<String, Object>> parts = scanRepository.findOrderParts(orderId);
        List<Map<String, Object>> cuts = agentCutsClient.listCutPieces(orderId, 500);
        if (cuts.isEmpty()) {
            return Map.of("orderId", orderId, "cuts", 0, "marked", 0, "skipped", 0);
        }

        Map<Integer, Long> partIdByNumber = new LinkedHashMap<>();
        for (Map<String, Object> part : parts) {
            if (!(part.get("partid") instanceof Number partIdNum)) {
                continue;
            }
            if (part.get("partnumber") instanceof Number pn && pn.intValue() > 0) {
                partIdByNumber.put(pn.intValue(), partIdNum.longValue());
            }
        }

        List<Map<String, Object>> sorted = new ArrayList<>(cuts);
        sorted.sort(
                Comparator.comparingLong(this::cutSortKey)
                        .thenComparingLong(c -> longVal(c.get("cut_piece_id"), 0L)));

        Map<Long, Integer> sequentialNext = new LinkedHashMap<>();
        int marked = 0;
        int skipped = 0;

        for (Map<String, Object> cut : sorted) {
            Long partId = cut.get("part_id") instanceof Number n ? n.longValue() : null;
            Integer pieceNum = pieceNumberFromCut(cut);
            if (partId == null || partId <= 0) {
                partId = resolvePartId(orderId, cut, partIdByNumber);
            }
            if (partId == null || partId <= 0) {
                skipped++;
                continue;
            }
            if (pieceNum == null || pieceNum <= 0) {
                pieceNum = sequentialNext.getOrDefault(partId, 0) + 1;
            }
            sequentialNext.put(partId, Math.max(sequentialNext.getOrDefault(partId, 0), pieceNum));

            String machine = str(cut.get("machine_name"));
            obrasRepository.ensurePiezaRow(partId, pieceNum);
            Map<String, Object> result = obrasRepository.markPiezaCortada(partId, pieceNum, machine);
            if (result != null) {
                marked++;
            } else {
                skipped++;
            }
        }

        log.debug("syncAgentCuts orderId={} cuts={} marked={} skipped={}", orderId, cuts.size(), marked, skipped);
        return Map.of(
                "orderId", orderId,
                "cuts", cuts.size(),
                "marked", marked,
                "skipped", skipped);
    }

    private long cutSortKey(Map<String, Object> cut) {
        Object raw = cut.get("created_at");
        if (raw instanceof java.sql.Timestamp ts) {
            return ts.getTime();
        }
        if (raw instanceof java.time.Instant i) {
            return i.toEpochMilli();
        }
        if (raw != null) {
            try {
                return java.sql.Timestamp.valueOf(String.valueOf(raw)).getTime();
            } catch (Exception ignored) {
                // fall through
            }
        }
        return longVal(cut.get("cut_piece_id"), 0L);
    }

    private Long resolvePartId(long orderId, Map<String, Object> cut, Map<Integer, Long> partIdByNumber) {
        Integer partNum = partNumberFromCut(cut);
        if (partNum != null && partIdByNumber.containsKey(partNum)) {
            return partIdByNumber.get(partNum);
        }
        Object osi = cut.get("osi_part_id");
        if (osi != null && !String.valueOf(osi).isBlank()) {
            Map<String, Object> part = obrasRepository.findPartForOsi(orderId, String.valueOf(osi).trim());
            if (part != null && part.get("partid") instanceof Number n) {
                return n.longValue();
            }
        }
        return null;
    }

    private static Integer pieceNumberFromCut(Map<String, Object> cut) {
        Integer fromUnit = pieceNumFromUnit(str(cut.get("unit_code")));
        if (fromUnit != null) {
            return fromUnit;
        }
        if (cut.get("piece_number") instanceof Number n && n.intValue() > 0) {
            return n.intValue();
        }
        return null;
    }

    private static Integer partNumberFromCut(Map<String, Object> cut) {
        Integer fromUnit = partNumFromUnit(str(cut.get("unit_code")));
        if (fromUnit != null) {
            return fromUnit;
        }
        Object osi = cut.get("osi_part_id");
        if (osi != null) {
            return BiesseObrasRepository.parsePartNumber(String.valueOf(osi));
        }
        return null;
    }

    private static Integer pieceNumFromUnit(String unit) {
        if (unit == null) {
            return null;
        }
        Matcher m = UNIT_CODE_SUFFIX.matcher(unit);
        if (!m.find()) {
            return null;
        }
        return Integer.parseInt(m.group(2));
    }

    private static Integer partNumFromUnit(String unit) {
        if (unit == null) {
            return null;
        }
        Matcher m = UNIT_CODE_SUFFIX.matcher(unit);
        if (!m.find()) {
            return null;
        }
        return Integer.parseInt(m.group(1));
    }

    private static String str(Object v) {
        if (v == null) {
            return null;
        }
        String t = String.valueOf(v).trim();
        return t.isEmpty() || "null".equalsIgnoreCase(t) ? null : t;
    }

    private static long longVal(Object v, long fallback) {
        return v instanceof Number n ? n.longValue() : fallback;
    }
}
