package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.EmployeeNotificationDtos;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeNotificationStreamService {

    private static final long EMITTER_TIMEOUT_MS = 0L;

    private final ObjectMapper objectMapper;
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByEmployee = new ConcurrentHashMap<>();

    public SseEmitter connect(long employeeId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emittersByEmployee
                .computeIfAbsent(employeeId, id -> new CopyOnWriteArrayList<>())
                .add(emitter);
        emitter.onCompletion(() -> removeEmitter(employeeId, emitter));
        emitter.onTimeout(() -> removeEmitter(employeeId, emitter));
        emitter.onError(ex -> removeEmitter(employeeId, emitter));
        try {
            emitter.send(
                    SseEmitter.event()
                            .name("connected")
                            .data(Map.of("employeeId", employeeId), MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            removeEmitter(employeeId, emitter);
        }
        return emitter;
    }

    public void pushToEmployee(long employeeId, EmployeeNotificationDtos.LiveNotificationPayload payload) {
        List<SseEmitter> emitters = emittersByEmployee.get(employeeId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(
                        SseEmitter.event()
                                .name(payload.event())
                                .data(payload, MediaType.APPLICATION_JSON));
            } catch (Exception ex) {
                removeEmitter(employeeId, emitter);
            }
        }
    }

    @Scheduled(fixedDelay = 25_000)
    void heartbeat() {
        for (Map.Entry<Long, CopyOnWriteArrayList<SseEmitter>> entry : emittersByEmployee.entrySet()) {
            Long employeeId = entry.getKey();
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (Exception ex) {
                    removeEmitter(employeeId, emitter);
                }
            }
        }
    }

    private void removeEmitter(long employeeId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emittersByEmployee.get(employeeId);
        if (list == null) {
            return;
        }
        list.remove(emitter);
        if (list.isEmpty()) {
            emittersByEmployee.remove(employeeId, list);
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // already closed
        }
    }
}
