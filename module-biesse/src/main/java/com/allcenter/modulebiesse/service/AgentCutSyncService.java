package com.allcenter.modulebiesse.service;

import com.allcenter.modulebiesse.obras.BiesseObrasRepository;
import com.allcenter.modulebiesse.repository.BiesseScanRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
 *
 * <p>Fuentes:
 * <ul>
 *   <li>{@code biesse_agent_cut_piece} (eventos / status procesados)
 *   <li>{@code last_part} vivo en máquinas con este job (si el evento falló pero el monitor sí lo ve)
 * </ul>
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

        Map<Integer, Long> partIdByNumber = new LinkedHashMap<>();
        for (Map<String, Object> part : parts) {
            if (!(part.get("partid") instanceof Number partIdNum)) {
                continue;
            }
            if (part.get("partnumber") instanceof Number pn && pn.intValue() > 0) {
                partIdByNumber.put(pn.intValue(), partIdNum.longValue());
            }
        }

        int marked = 0;
        int skipped = 0;

        List<Map<String, Object>> sorted = new ArrayList<>(cuts);
        sorted.sort(
                Comparator.comparingLong(this::cutSortKey)
                        .thenComparingLong(c -> longVal(c.get("cut_piece_id"), 0L)));

        for (Map<String, Object> cut : sorted) {
            Long partId = cut.get("part_id") instanceof Number n ? n.longValue() : null;
            Integer pieceNum = pieceNumberFromCut(cut);
            String eventUid = str(cut.get("event_uid"));
            if (partId == null || partId <= 0) {
                partId = resolvePartId(orderId, cut, partIdByNumber);
            }
            if (partId == null || partId <= 0) {
                skipped++;
                continue;
            }
            // Cortes sintéticos del status sin N de pieza: no avanzar 1..cantidad (events mandan).
            if ((pieceNum == null || pieceNum <= 0)
                    && eventUid != null
                    && eventUid.regionMatches(true, 0, "status-cut-", 0, "status-cut-".length())) {
                skipped++;
                continue;
            }
            if (pieceNum == null || pieceNum <= 0) {
                Integer next = obrasRepository.resolvePieceNumberForCut(partId, null);
                if (next == null) {
                    skipped++;
                    continue;
                }
                pieceNum = next;
            }

            String machine = str(cut.get("machine_name"));
            if (!obrasRepository.ensurePiezaRow(partId, pieceNum)) {
                Integer qty = obrasRepository.partCantidad(partId);
                if (qty != null && qty > 0) {
                    obrasRepository.markPiezaCorteError(
                            partId,
                            qty,
                            "Sync: captura fuera de cantidad (" + pieceNum + ">" + qty + ")");
                }
                skipped++;
                continue;
            }
            // Idempotente: no inflar corte_count en cada poll.
            Map<String, Object> result = obrasRepository.markPiezaCortada(partId, pieceNum, machine, false);
            if (result != null) {
                if (Boolean.TRUE.equals(result.get("updated"))) {
                    marked++;
                }
            } else {
                obrasRepository.markPiezaCorteError(
                        partId, pieceNum, "Sync monitor: no se pudo marcar cortada");
                skipped++;
            }
        }

        // Respaldo: last_part del monitor (aunque no haya filas en cut_piece).
        int fromStatus = syncFromMachineLastParts(orderId, partIdByNumber);
        marked += fromStatus;

        log.debug(
                "syncAgentCuts orderId={} cuts={} marked={} skipped={} fromStatus={}",
                orderId,
                cuts.size(),
                marked,
                skipped,
                fromStatus);

        boolean produccion = false;
        if (marked > 0 || !cuts.isEmpty() || fromStatus > 0) {
            produccion = obrasRepository.markOrderProduccion(orderId);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("orderId", orderId);
        out.put("cuts", cuts.size());
        out.put("marked", marked);
        out.put("skipped", skipped);
        out.put("fromStatus", fromStatus);
        out.put("produccion", produccion);
        return out;
    }

    /**
     * Si el monitor muestra {@code Part P146 …} en una máquina de esta obra y la BD aún no tiene
     * esa pieza cortada, la marca (siguiente número libre 1..cantidad).
     */
    private int syncFromMachineLastParts(long orderId, Map<Integer, Long> partIdByNumber) {
        Map<String, Object> order = obrasRepository.findOrderById(orderId);
        String orderName = order != null ? str(order.get("ordername")) : null;
        List<Map<String, Object>> machines = agentCutsClient.listMachinesForOrder(orderId, orderName);
        if (machines.isEmpty()) {
            return 0;
        }
        int marked = 0;
        for (Map<String, Object> machine : machines) {
            String lastPart = str(machine.get("last_part"));
            if (lastPart == null || !lastPart.regionMatches(true, 0, "Part", 0, 4)) {
                continue;
            }
            Long partId = resolvePartIdFromOsi(orderId, lastPart, partIdByNumber);
            if (partId == null) {
                continue;
            }
            Integer pieceNum = obrasRepository.nextPieceNumber(partId);
            // last_part solo prueba que esa parte se cortó al menos una vez;
            // no inventar pieza 2+ (eso viene de cut_piece / events).
            if (pieceNum == null || pieceNum != 1) {
                continue;
            }
            String machineName = str(machine.get("machine_name"));
            if (!obrasRepository.ensurePiezaRow(partId, pieceNum)) {
                continue;
            }
            Map<String, Object> result = obrasRepository.markPiezaCortada(partId, pieceNum, machineName);
            if (result != null && Boolean.TRUE.equals(result.get("updated"))) {
                marked++;
                log.info(
                        "Cortada desde last_part monitor: order={} partId={} piece={} machine={} osi={}",
                        orderId,
                        partId,
                        pieceNum,
                        machineName,
                        lastPart);
            }
        }
        return marked;
    }

    private Long resolvePartIdFromOsi(
            long orderId, String osiText, Map<Integer, Long> partIdByNumber) {
        Integer partNum = BiesseObrasRepository.parsePartNumber(osiText);
        if (partNum != null && partIdByNumber.containsKey(partNum)) {
            return partIdByNumber.get(partNum);
        }
        Map<String, Object> part = obrasRepository.findPartForOsi(orderId, osiText);
        if (part != null && part.get("partid") instanceof Number n) {
            return n.longValue();
        }
        return null;
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
