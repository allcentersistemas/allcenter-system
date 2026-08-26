package com.allcenter.modulesystem.agent;

import com.allcenter.modulesystem.agent.BiesseAgentDtos.AgentEventDto;
import com.allcenter.modulesystem.agent.BiesseAgentDtos.EventsRequest;
import com.allcenter.modulesystem.agent.BiesseAgentDtos.EventsResponse;
import com.allcenter.modulesystem.agent.BiesseAgentDtos.HeartbeatRequest;
import com.allcenter.modulesystem.agent.BiesseAgentDtos.LabelDto;
import com.allcenter.modulesystem.agent.BiesseAgentDtos.MeResponse;
import com.allcenter.modulesystem.agent.BiesseAgentDtos.OkResponse;
import com.allcenter.modulesystem.agent.BiesseAgentDtos.PrintAckItem;
import com.allcenter.modulesystem.agent.BiesseAgentDtos.PrintAckRequest;
import com.allcenter.modulesystem.agent.BiesseAgentDtos.StatusPayload;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BiesseAgentService {

    private static final Logger log = LoggerFactory.getLogger(BiesseAgentService.class);
    private static final Pattern START_PROGRAM =
            Pattern.compile("^Start program;([^;]*);([^;]*)(?:;|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_SUFFIX = Pattern.compile("\\.(\\d{3})$");

    private final BiesseAgentRepository repository;
    private final BiesseObrasClient obrasClient;

    public MeResponse me(Map<String, Object> machine) {
        return new MeResponse(
                true,
                ((Number) machine.get("machine_id")).intValue(),
                str(machine.get("machine_name")),
                machine.get("company_id") != null ? ((Number) machine.get("company_id")).intValue() : null,
                bool(machine.get("online")),
                str(machine.get("state")),
                "ok");
    }

    @Transactional
    public OkResponse heartbeat(Map<String, Object> machine, HeartbeatRequest req) {
        int machineId = ((Number) machine.get("machine_id")).intValue();
        repository.updateHeartbeat(machineId, req != null ? req : emptyHeartbeat());
        return OkResponse.success();
    }

    @Transactional
    public OkResponse status(Map<String, Object> machine, StatusPayload status) {
        if (status == null) {
            return OkResponse.success();
        }
        int machineId = ((Number) machine.get("machine_id")).intValue();
        String jobName = status.jobName();
        Map<String, Object> order = obrasClient.findOrderForJob(jobName);
        Long orderId = order != null ? ((Number) order.get("orderid")).longValue() : null;

        repository.updateStatus(machineId, status, orderId);

        Map<String, Object> live = repository.findMachineById(machineId);
        if (live == null) {
            live = machine;
        }

        String state = status.state() != null ? status.state().trim().toUpperCase(Locale.ROOT) : "";
        boolean cutting = "RUN".equals(state)
                || (status.activeCommand() != null
                        && status.activeCommand().toLowerCase(Locale.ROOT).contains("start program"));

        if (cutting && order != null) {
            markProduccionAndTrace(live, order, status.eventTime(), "STATUS_RUN");
        }

        if (("IDLE".equals(state) || "UNKNOWN".equals(state)) && live.get("job_started_at") != null) {
            closeCuttingWindow(live, order, status.eventTime(), "STATUS_IDLE");
        }

        return OkResponse.success();
    }

    @Transactional
    public EventsResponse events(Map<String, Object> machine, EventsRequest request) {
        int machineId = ((Number) machine.get("machine_id")).intValue();
        boolean printLocal = bool(machine.get("printer_enabled"));
        String machineName = str(machine.get("machine_name"));

        int accepted = 0;
        int duplicates = 0;
        List<LabelDto> labels = new ArrayList<>();

        for (AgentEventDto ev : request.eventsOrEmpty()) {
            if (ev == null || ev.eventUid() == null || ev.eventUid().isBlank()) {
                continue;
            }
            if (repository.eventExists(ev.eventUid())) {
                duplicates++;
                continue;
            }

            Instant eventTime = BiesseAgentRepository.parseEventTime(ev.eventTime());
            String type = ev.eventType() != null ? ev.eventType().trim() : "";
            String desc = ev.description() != null ? ev.description().trim() : "";
            String code = ev.code() != null ? ev.code().trim() : "";
            String action = "INGESTED";
            Long orderId = null;

            if (isStartProgram(type, desc)) {
                String job = parseJobName(desc);
                Map<String, Object> order = obrasClient.findOrderForJob(job);
                if (order != null) {
                    orderId = ((Number) order.get("orderid")).longValue();
                    markProduccionAndTrace(machine, order, ev.eventTime(), "START_PROGRAM");
                    action = "PRODUCCION";
                } else {
                    action = "START_NO_MATCH";
                    log.info("Start program sin obra: job='{}' machine={}", job, machineId);
                }
            }

            if (isProductInfoPart(type, desc, code)) {
                String osiPart = !desc.isBlank() ? desc : code;
                Map<String, Object> live = repository.findMachineById(machineId);
                Map<String, Object> order =
                        obrasClient.findOrderForJob(
                                live != null ? str(live.get("job_name")) : str(machine.get("job_name")));
                if (order != null) {
                    orderId = ((Number) order.get("orderid")).longValue();
                    LabelDto label =
                            buildLabelForPart(
                                    machineId, machineName, order, osiPart, ev.eventUid(), printLocal);
                    if (label != null) {
                        labels.add(label);
                        action = "LABEL";
                    } else {
                        action = "PART_UNMAPPED";
                    }
                    obrasClient.registrarTrazabilidad(
                            opOf(order),
                            orderId,
                            str(order.get("ordername")),
                            "PRODUCCION",
                            "PIEZA_CORTADA",
                            "OSI " + osiPart + " @ " + ev.eventTime(),
                            0,
                            0,
                            "AGENTE:" + machineName);
                } else {
                    action = "PART_NO_ORDER";
                }
            }

            if ("Boards done".equalsIgnoreCase(type) && machine.get("job_name") != null) {
                Map<String, Object> order = obrasClient.findOrderForJob(str(machine.get("job_name")));
                if (order != null) {
                    orderId = ((Number) order.get("orderid")).longValue();
                    obrasClient.registrarTrazabilidad(
                            opOf(order),
                            orderId,
                            str(order.get("ordername")),
                            "PRODUCCION",
                            "BOARDS_DONE",
                            "Boards done: " + code + " " + desc + " @ " + ev.eventTime(),
                            0,
                            0,
                            "AGENTE:" + machineName);
                    action = "BOARDS_DONE";
                }
            }

            if ("State".equalsIgnoreCase(type)
                    && desc != null
                    && desc.equalsIgnoreCase("Idle")
                    && machine.get("job_started_at") != null) {
                Map<String, Object> order =
                        machine.get("job_name") != null
                                ? obrasClient.findOrderForJob(str(machine.get("job_name")))
                                : null;
                closeCuttingWindow(machine, order, ev.eventTime(), "EVENT_IDLE");
                action = "CORTE_FIN";
            }

            repository.insertEvent(
                    ev.eventUid(),
                    machineId,
                    type,
                    code,
                    desc,
                    ev.severity(),
                    eventTime,
                    orderId,
                    action);
            accepted++;
        }

        if (request.logByteOffset() != null || request.pendingQueueSize() != null) {
            repository.updateHeartbeat(
                    machineId,
                    new HeartbeatRequest(
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            request.pendingQueueSize(),
                            request.logByteOffset(),
                            null,
                            request.healthStatus(),
                            null));
        }

        String printMode = printLocal ? "LOCAL" : "NONE";
        return EventsResponse.of(accepted, duplicates, printMode, labels);
    }

    @Transactional
    public OkResponse printAck(Map<String, Object> machine, PrintAckRequest request) {
        for (PrintAckItem item : request.labelsOrEmpty()) {
            if (item == null) {
                continue;
            }
            repository.ackPrint(item.cutPieceId(), item.eventUid(), item.printed(), item.error());
        }
        return OkResponse.success();
    }

    private void markProduccionAndTrace(
            Map<String, Object> machine, Map<String, Object> order, String eventTime, String source) {
        long orderId = ((Number) order.get("orderid")).longValue();
        String orderName = str(order.get("ordername"));
        String op = opOf(order);
        int machineId = ((Number) machine.get("machine_id")).intValue();
        Instant started = BiesseAgentRepository.parseEventTime(eventTime);

        boolean changed = obrasClient.markOrderProduccion(orderId);
        repository.markJobStarted(machineId, orderId, started);

        if (changed) {
            obrasClient.registrarTrazabilidad(
                    op,
                    orderId,
                    orderName,
                    "PRODUCCION",
                    "CORTE_INICIO",
                    "Inicio de corte ("
                            + source
                            + ") máquina="
                            + str(machine.get("machine_name"))
                            + " job="
                            + str(machine.get("job_name"))
                            + " t="
                            + eventTime,
                    0,
                    intOrZero(order.get("partes_totales")),
                    "AGENTE:" + str(machine.get("machine_name")));
            log.info("Obra {} → PRODUCCION (fuente={})", orderName, source);
        }
    }

    private void closeCuttingWindow(
            Map<String, Object> machine, Map<String, Object> order, String eventTime, String source) {
        Object startedObj = machine.get("job_started_at");
        if (startedObj == null) {
            return;
        }
        Instant started;
        if (startedObj instanceof java.sql.Timestamp ts) {
            started = ts.toInstant();
        } else if (startedObj instanceof Instant i) {
            started = i;
        } else {
            started = Instant.now();
        }
        Instant ended = BiesseAgentRepository.parseEventTime(eventTime);
        long seconds = Math.max(0, Duration.between(started, ended).getSeconds());

        int machineId = ((Number) machine.get("machine_id")).intValue();
        repository.clearJobStarted(machineId);

        if (order == null) {
            return;
        }
        long orderId = ((Number) order.get("orderid")).longValue();
        String orderName = str(order.get("ordername"));
        obrasClient.registrarTrazabilidad(
                opOf(order),
                orderId,
                orderName,
                "PRODUCCION",
                "CORTE_FIN",
                "Fin/pausa corte ("
                        + source
                        + ") duración="
                        + seconds
                        + "s ("
                        + formatDuration(seconds)
                        + ") t="
                        + eventTime,
                0,
                0,
                "AGENTE:" + str(machine.get("machine_name")));
    }

    private LabelDto buildLabelForPart(
            int machineId,
            String machineName,
            Map<String, Object> order,
            String osiPart,
            String eventUid,
            boolean printLocal) {
        long orderId = ((Number) order.get("orderid")).longValue();
        Map<String, Object> mapped = obrasClient.partForOsi(orderId, osiPart, machineName);
        if (mapped == null) {
            return null;
        }
        String mapStatus = str(mapped.get("mapStatus"));
        String unitCode = str(mapped.get("unitCode"));
        String zpl = str(mapped.get("zpl"));
        Long partId =
                mapped.get("partId") instanceof Number n ? n.longValue() : null;

        long cutId =
                repository.insertCutPiece(
                        eventUid, machineId, orderId, partId, osiPart, unitCode, mapStatus, zpl);
        return new LabelDto(cutId, eventUid, osiPart, unitCode, mapStatus, zpl, printLocal);
    }

    private static String opOf(Map<String, Object> order) {
        String op = str(order.get("op_codigo"));
        if (op == null || op.isBlank()) {
            op = BiesseAgentRepository.extractOp(str(order.get("ordername")));
        }
        return op;
    }

    private static boolean isStartProgram(String type, String desc) {
        if (!"Comand".equalsIgnoreCase(type) && !"Command".equalsIgnoreCase(type)) {
            return false;
        }
        return desc != null && desc.regionMatches(true, 0, "Start program", 0, "Start program".length());
    }

    private static boolean isProductInfoPart(String type, String desc, String code) {
        if (!"PRODUCT INFO".equalsIgnoreCase(type)) {
            return false;
        }
        String text = (desc != null && !desc.isBlank()) ? desc : code;
        return text != null && text.regionMatches(true, 0, "Part", 0, 4);
    }

    private static String parseJobName(String description) {
        if (description == null) {
            return "";
        }
        Matcher m = START_PROGRAM.matcher(description.trim());
        if (!m.find()) {
            return "";
        }
        String field1 = m.group(1).trim();
        String field2 = m.group(2).trim();
        String job;
        if ("SINGLE".equalsIgnoreCase(field1)) {
            job = splitJob(field2);
        } else {
            job = field1;
        }
        if ("SINGLE".equalsIgnoreCase(job)) {
            job = splitJob(field2);
        }
        return job;
    }

    private static String splitJob(String value) {
        value = value != null ? value.trim() : "";
        Matcher suffix = PATTERN_SUFFIX.matcher(value);
        if (suffix.find()) {
            return value.substring(0, value.length() - suffix.group().length()).trim();
        }
        return value;
    }

    private static HeartbeatRequest emptyHeartbeat() {
        return new HeartbeatRequest(null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static boolean bool(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        return o != null && Boolean.parseBoolean(String.valueOf(o));
    }

    private static int intOrZero(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private static String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) {
            return h + "h " + m + "m " + s + "s";
        }
        if (m > 0) {
            return m + "m " + s + "s";
        }
        return s + "s";
    }
}
