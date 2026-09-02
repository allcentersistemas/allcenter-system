package com.allcenter.modulesystem.agent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Canal en vivo (SSE) del monitor de seccionadoras. Empuja machines + boards/live
 * mientras haya suscriptores (mismo patrón que SeguimientoLiveHub).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BiesseMonitorLiveHub {

    private static final long EMITTER_TIMEOUT_MS = 0L;

    private final BiesseAgentRepository agentRepository;
    private final BiesseAgentSchemaAligner schemaAligner;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private volatile String lastFingerprint = "";

    public SseEmitter connect() {
        schemaAligner.ensureReady();
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));

        try {
            Map<String, Object> snap = buildSnapshot();
            lastFingerprint = fingerprint(snap);
            emitter.send(
                    SseEmitter.event()
                            .name("connected")
                            .data(Map.of("ok", true), MediaType.APPLICATION_JSON));
            emitter.send(SseEmitter.event().name("snapshot").data(snap, MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public boolean hasSubscribers() {
        return !emitters.isEmpty();
    }

    /** Snapshot machines + boards live (también usable por el REST). */
    public Map<String, Object> buildSnapshot() {
        schemaAligner.ensureReady();
        List<Map<String, Object>> machines = agentRepository.listMachines();
        List<Map<String, Object>> boardRows = new ArrayList<>();
        int totalLive = 0;
        int totalOnline = 0;
        for (Map<String, Object> m : machines) {
            int machineId = ((Number) m.get("machine_id")).intValue();
            boolean online = Boolean.TRUE.equals(m.get("online"));
            String state = m.get("state") != null ? String.valueOf(m.get("state")) : null;
            String stateUpper = state != null ? state.trim().toUpperCase(Locale.ROOT) : "";
            int boardsDone = m.get("boards_done") instanceof Number n ? n.intValue() : 0;
            int boardsToday = agentRepository.sumBoardsToday(machineId);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("machine_id", machineId);
            row.put("machine_name", m.get("machine_name"));
            row.put("plant_name", m.get("plant_name"));
            row.put("online", online);
            row.put("state", state);
            row.put("job_name", m.get("job_name"));
            row.put("boards_done", boardsDone);
            row.put("boards_today", boardsToday);
            row.put("pieces_produced", m.get("pieces_produced"));
            row.put("job_started_at", m.get("job_started_at"));
            row.put("last_status_at", m.get("last_status_at"));
            row.put("last_heartbeat_at", m.get("last_heartbeat_at"));
            row.put("current_order_id", m.get("current_order_id"));
            boardRows.add(row);

            if (online) {
                totalOnline += boardsDone;
                if ("RUN".equals(stateUpper)) {
                    totalLive += boardsDone;
                }
            }
        }

        Map<String, Object> boardsLive = new LinkedHashMap<>();
        boardsLive.put("machines", boardRows);
        boardsLive.put("total_live", totalLive);
        boardsLive.put("total_online", totalOnline);
        boardsLive.put("total_today", agentRepository.sumBoardsToday(null));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("machines", machines);
        out.put("boards_live", boardsLive);
        out.put("server_time", java.time.Instant.now().toString());
        return out;
    }

    @Scheduled(fixedDelay = 2_000)
    void watchAndPush() {
        if (!hasSubscribers()) {
            return;
        }
        Map<String, Object> snap;
        try {
            snap = buildSnapshot();
        } catch (Exception ex) {
            log.debug("monitor live fetch failed: {}", ex.getMessage());
            return;
        }
        String fp = fingerprint(snap);
        if (fp.equals(lastFingerprint)) {
            // Keep-alive ligero para proxies
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data(Map.of("t", System.currentTimeMillis())));
                } catch (Exception ex) {
                    emitters.remove(emitter);
                }
            }
            return;
        }
        lastFingerprint = fp;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("update").data(snap, MediaType.APPLICATION_JSON));
            } catch (Exception ex) {
                emitters.remove(emitter);
            }
        }
    }

    private static String fingerprint(Map<String, Object> snap) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> machines = (List<Map<String, Object>>) snap.get("machines");
            StringBuilder sb = new StringBuilder();
            if (machines != null) {
                for (Map<String, Object> m : machines) {
                    sb.append(m.get("machine_id"))
                            .append('|')
                            .append(m.get("online"))
                            .append('|')
                            .append(m.get("state"))
                            .append('|')
                            .append(m.get("job_name"))
                            .append('|')
                            .append(m.get("boards_done"))
                            .append('|')
                            .append(m.get("pieces_produced"))
                            .append('|')
                            .append(m.get("job_started_at"))
                            .append('|')
                            .append(m.get("last_heartbeat_at"))
                            .append('|')
                            .append(m.get("last_status_at"))
                            .append(';');
                }
            }
            Object boards = snap.get("boards_live");
            if (boards instanceof Map<?, ?> bl) {
                sb.append(bl.get("total_live")).append('/').append(bl.get("total_today"));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }
}
