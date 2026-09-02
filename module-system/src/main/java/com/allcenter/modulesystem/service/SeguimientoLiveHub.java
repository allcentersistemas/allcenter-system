package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.OrderDtos;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Hub SSE del tablero Resumen → Seguimiento. Empuja snapshot/update cuando hay suscriptores.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SeguimientoLiveHub {

    private static final long EMITTER_TIMEOUT_MS = 0L;

    private final OrderPersistenceService orderPersistenceService;
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersBySince = new ConcurrentHashMap<>();
    private final Map<String, String> lastFingerprintBySince = new ConcurrentHashMap<>();

    public SseEmitter connect(String since) {
        String key = normalizeSince(since);
        String mapKey = key == null ? "" : key;
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emittersBySince.computeIfAbsent(mapKey, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(mapKey, emitter));
        emitter.onTimeout(() -> removeEmitter(mapKey, emitter));
        emitter.onError(ex -> removeEmitter(mapKey, emitter));

        try {
            emitter.send(
                    SseEmitter.event()
                            .name("connected")
                            .data(Map.of("since", mapKey), MediaType.APPLICATION_JSON));
            List<OrderDtos.SeguimientoObraResponse> obras = orderPersistenceService.listSeguimientoObras(key);
            lastFingerprintBySince.put(mapKey, fingerprint(obras));
            emitter.send(SseEmitter.event().name("snapshot").data(obras, MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            removeEmitter(mapKey, emitter);
        }
        return emitter;
    }

    public boolean hasSubscribers() {
        for (CopyOnWriteArrayList<SseEmitter> list : emittersBySince.values()) {
            if (list != null && !list.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Scheduled(fixedDelay = 2_000)
    void watchAndPush() {
        if (!hasSubscribers()) {
            return;
        }
        for (String sinceKey : emittersBySince.keySet()) {
            CopyOnWriteArrayList<SseEmitter> emitters = emittersBySince.get(sinceKey);
            if (emitters == null || emitters.isEmpty()) {
                continue;
            }
            String sinceParam = sinceKey.isEmpty() ? null : sinceKey;
            List<OrderDtos.SeguimientoObraResponse> obras;
            try {
                obras = orderPersistenceService.listSeguimientoObras(sinceParam);
            } catch (Exception ex) {
                log.debug("seguimiento live fetch failed (since={}): {}", sinceKey, ex.getMessage());
                continue;
            }
            String fp = fingerprint(obras);
            String prev = lastFingerprintBySince.put(sinceKey, fp);
            if (Objects.equals(prev, fp)) {
                continue;
            }
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("update").data(obras, MediaType.APPLICATION_JSON));
                } catch (Exception ex) {
                    removeEmitter(sinceKey, emitter);
                }
            }
        }
    }

    @Scheduled(fixedDelay = 25_000)
    void heartbeat() {
        for (Map.Entry<String, CopyOnWriteArrayList<SseEmitter>> entry : emittersBySince.entrySet()) {
            String sinceKey = entry.getKey();
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (Exception ex) {
                    removeEmitter(sinceKey, emitter);
                }
            }
        }
    }

    private void removeEmitter(String since, SseEmitter emitter) {
        String key = normalizeSince(since);
        String mapKey = key == null ? "" : key;
        CopyOnWriteArrayList<SseEmitter> list = emittersBySince.get(mapKey);
        if (list == null) {
            return;
        }
        list.remove(emitter);
        if (list.isEmpty()) {
            emittersBySince.remove(mapKey, list);
            lastFingerprintBySince.remove(mapKey);
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // already closed
        }
    }

    private static String normalizeSince(String since) {
        if (since == null) {
            return null;
        }
        String t = since.trim();
        return t.isEmpty() ? null : t;
    }

    private static String fingerprint(List<OrderDtos.SeguimientoObraResponse> obras) {
        if (obras == null || obras.isEmpty()) {
            return "empty";
        }
        StringBuilder sb = new StringBuilder(obras.size() * 48);
        for (OrderDtos.SeguimientoObraResponse o : obras) {
            sb.append(o.orderId())
                    .append('|')
                    .append(o.estadoEscaneo())
                    .append('|')
                    .append(o.porcentaje())
                    .append('|')
                    .append(o.porcentajeCorte())
                    .append('|')
                    .append(o.avanceLabel())
                    .append('|')
                    .append(o.avanceCorteLabel())
                    .append(';');
        }
        return Integer.toHexString(sb.toString().hashCode());
    }
}
