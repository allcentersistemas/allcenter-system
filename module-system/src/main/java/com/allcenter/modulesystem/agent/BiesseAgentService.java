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
import com.allcenter.modulesystem.service.FulfillmentService;
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
    /** Número OSI en líneas "Part P51 …" (misma lógica que module-biesse). */
    private static final Pattern OSI_PART_KEY =
            Pattern.compile("(?i)^Part\\s*(P?\\d+)");

    private final BiesseAgentRepository repository;
    private final BiesseObrasClient obrasClient;
    private final FulfillmentService fulfillmentService;

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

    public Map<String, Object> orderManifest(Map<String, Object> machine, String jobName) {
        if (jobName == null || jobName.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "job requerido");
        }
        Map<String, Object> resolve = obrasClient.resolveOrderForJob(jobName.trim());
        if (Boolean.TRUE.equals(resolve.get("ambiguous"))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Obra ambigua para job «" + jobName.trim() + "»");
        }
        Map<String, Object> manifest = obrasClient.orderManifest(jobName.trim());
        if (manifest == null || manifest.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Manifiesto no encontrado para job");
        }
        return manifest;
    }

    public Map<String, Object> labelZpl(
            Map<String, Object> machine,
            String jobName,
            String osiPart,
            int pieceNumber,
            String unitCode) {
        String machineName = str(machine.get("machine_name"));
        String zpl = obrasClient.labelZpl(jobName, osiPart, pieceNumber, unitCode, machineName);
        if (zpl == null || zpl.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "ZPL no disponible");
        }
        return Map.of("zpl", zpl);
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
        String machineName = str(machine.get("machine_name"));
        String jobName = status.jobName();
        Map<String, Object> order = obrasClient.findOrderForJob(jobName);
        Long orderId = order != null ? ((Number) order.get("orderid")).longValue() : null;

        Integer previousBoards = intOrNull(machine.get("boards_done"));
        String previousLastPart = str(machine.get("last_part"));
        Integer previousPieces = intOrNull(machine.get("pieces_produced"));
        // Si el status llegó tras un evento reciente, machine puede estar desactualizado: releer.
        Map<String, Object> before = repository.findMachineById(machineId);
        if (before != null) {
            previousBoards = intOrNull(before.get("boards_done"));
            previousLastPart = str(before.get("last_part"));
            previousPieces = intOrNull(before.get("pieces_produced"));
            if (jobName == null || jobName.isBlank()) {
                jobName = str(before.get("job_name"));
                if (order == null && jobName != null && !jobName.isBlank()) {
                    order = obrasClient.findOrderForJob(jobName);
                    orderId = order != null ? ((Number) order.get("orderid")).longValue() : null;
                }
            }
            if (orderId == null && before.get("current_order_id") instanceof Number n) {
                orderId = n.longValue();
            }
        }
        if (order == null && orderId != null) {
            order = obrasClient.findOrderById(orderId);
        }
        if (order == null && jobName != null && !jobName.isBlank()) {
            log.warn("Status job sin obra en ERP: job='{}' machine={}", jobName, machineId);
        }

        repository.updateStatus(machineId, status, orderId);

        recordBoardsFromStatusDelta(
                machineId,
                machineName,
                jobName,
                orderId,
                previousBoards,
                status.boardsDone(),
                BiesseAgentRepository.parseEventTime(status.eventTime()));

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

        // Respaldo: si llega last_part nueva por status (y el evento PRODUCT INFO no se procesó),
        // registrar corte / sticker igual que con el Event.log.
        processCutFromStatus(
                machineId,
                machineName,
                bool(machine.get("printer_enabled")),
                live,
                order,
                previousLastPart,
                previousPieces,
                status);

        if (("IDLE".equals(state) || "UNKNOWN".equals(state)) && live.get("job_started_at") != null) {
            closeCuttingWindow(live, order, status.eventTime(), "STATUS_IDLE");
        }

        return OkResponse.success();
    }

    /**
     * Cuando {@code last_part} / piezas de sesión avanzan en el status, genera corte MAPPED.
     * Idempotente por event_uid sintético ({@code status-cut-…}).
     */
    private void processCutFromStatus(
            int machineId,
            String machineName,
            boolean printLocal,
            Map<String, Object> machineCtx,
            Map<String, Object> order,
            String previousLastPart,
            Integer previousPieces,
            StatusPayload status) {
        if (order == null || status == null) {
            return;
        }
        String lastPart = status.lastPart() != null ? status.lastPart().trim() : "";
        if (lastPart.isBlank() || !lastPart.regionMatches(true, 0, "Part", 0, 4)) {
            return;
        }
        Integer pieces = status.piecesProduced();
        boolean partChanged =
                previousLastPart == null
                        || previousLastPart.isBlank()
                        || !lastPart.equalsIgnoreCase(previousLastPart.trim());
        boolean piecesAdvanced =
                pieces != null && (previousPieces == null || pieces > previousPieces);
        if (!partChanged && !piecesAdvanced) {
            return;
        }

        long orderId = ((Number) order.get("orderid")).longValue();
        String partKey = osiPartKey(lastPart);
        String sessionKey =
                pieces != null && pieces > 0
                        ? String.valueOf(pieces)
                        : Integer.toHexString(lastPart.toLowerCase(Locale.ROOT).hashCode());
        // Incluir parte OSI en el uid: evita colisión cuando varias piezas comparten el mismo contador de sesión.
        String eventUid = "status-cut-" + machineId + "-" + orderId + "-" + partKey + "-s" + sessionKey;
        if (repository.eventExists(eventUid)) {
            return;
        }
        if (repository.hasRecentCutForOsi(machineId, orderId, lastPart, 180)) {
            return;
        }

        markProduccionAndTrace(machineCtx, order, status.eventTime(), "STATUS_LAST_PART");

        LabelDto label =
                buildLabelForPart(machineId, machineName, order, lastPart, eventUid, printLocal);
        Instant eventTime = BiesseAgentRepository.parseEventTime(status.eventTime());
        repository.insertEvent(
                eventUid,
                machineId,
                "PRODUCT INFO",
                "",
                lastPart,
                "INFO",
                eventTime,
                orderId,
                label != null ? "LABEL" : "PART_UNMAPPED");
        if (label != null) {
            log.info(
                    "Corte desde status: machine={} order={} part={} uid={}",
                    machineId,
                    orderId,
                    lastPart,
                    eventUid);
        } else {
            log.warn(
                    "Status last_part sin mapa: machine={} order={} part='{}'",
                    machineId,
                    orderId,
                    lastPart);
        }
    }

    @Transactional
    public EventsResponse events(Map<String, Object> machine, EventsRequest request) {
        int machineId = ((Number) machine.get("machine_id")).intValue();
        boolean printLocal = bool(machine.get("printer_enabled"));
        String machineName = str(machine.get("machine_name"));

        int accepted = 0;
        int duplicates = 0;
        List<LabelDto> labels = new ArrayList<>();
        // Cursor de planchas dentro del batch (varios "Boards done" antes del status).
        int boardsSessionTotal = -1;
        boolean boardsJobRestart = false;

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
                Map<String, Object> resolve = obrasClient.resolveOrderForJob(job);
                if (Boolean.TRUE.equals(resolve.get("ambiguous"))) {
                    action = "START_AMBIGUOUS";
                    log.warn("Start program ambiguo: job='{}' machine={}", job, machineId);
                } else {
                    Object orderObj = resolve.get("order");
                    Map<String, Object> order =
                            orderObj instanceof Map<?, ?> m
                                    ? (Map<String, Object>) m
                                    : obrasClient.findOrderForJob(job);
                    if (order != null) {
                        orderId = ((Number) order.get("orderid")).longValue();
                        markProduccionAndTrace(machine, order, ev.eventTime(), "START_PROGRAM");
                        action = "PRODUCCION";
                    } else {
                        action = "START_NO_MATCH";
                        log.info("Start program sin obra: job='{}' machine={}", job, machineId);
                    }
                }
            }

            if ("Message".equalsIgnoreCase(type)) {
                action = "OSI_ALARM";
            }

            if (isProductInfoPart(type, desc, code)) {
                String osiPart = !desc.isBlank() ? desc : code;
                Map<String, Object> live = repository.findMachineById(machineId);
                Map<String, Object> machineCtx = live != null ? live : machine;
                Map<String, Object> order =
                        obrasClient.findOrderForJob(
                                live != null ? str(live.get("job_name")) : str(machine.get("job_name")));
                if (order != null) {
                    orderId = ((Number) order.get("orderid")).longValue();
                    markProduccionAndTrace(machineCtx, order, ev.eventTime(), "PIEZA_CORTADA");
                    boolean printedLocally = Boolean.TRUE.equals(ev.printedLocally());
                    LabelDto label =
                            buildLabelForPart(
                                    machineId,
                                    machineName,
                                    order,
                                    osiPart,
                                    ev.eventUid(),
                                    printLocal && !printedLocally,
                                    ev.pieceNumber(),
                                    ev.unitCode());
                    if (label != null && printLocal && !printedLocally) {
                        labels.add(label);
                        action = "LABEL";
                    } else if (label != null) {
                        action = printedLocally ? "LABEL_LOCAL" : "PART_UNMAPPED";
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

            if ("Boards done".equalsIgnoreCase(type)) {
                Map<String, Object> live = repository.findMachineById(machineId);
                String jobName =
                        live != null && live.get("job_name") != null
                                ? str(live.get("job_name"))
                                : str(machine.get("job_name"));
                Map<String, Object> order =
                        jobName != null && !jobName.isBlank()
                                ? obrasClient.findOrderForJob(jobName)
                                : null;
                if (order != null) {
                    orderId = ((Number) order.get("orderid")).longValue();
                    markProduccionAndTrace(
                            live != null ? live : machine, order, ev.eventTime(), "BOARDS_DONE");
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
                } else {
                    action = "BOARDS_DONE_NO_ORDER";
                }
                int boardsDelta = parseBoardsDelta(code);
                if (boardsSessionTotal < 0) {
                    int maxAfter = repository.maxBoardsTotalAfter(machineId, jobName);
                    Integer liveBoards =
                            intOrNull(live != null ? live.get("boards_done") : machine.get("boards_done"));
                    // Reinicio de job: el contador vivo bajó respecto al histórico del mismo job_name.
                    if (liveBoards != null && liveBoards < maxAfter) {
                        boardsSessionTotal = liveBoards;
                        boardsJobRestart = true;
                    } else {
                        boardsSessionTotal = maxAfter;
                    }
                }
                boardsSessionTotal += boardsDelta;
                boolean alreadyCounted =
                        !boardsJobRestart
                                && repository.boardCutExistsForTotal(
                                        machineId, jobName, boardsSessionTotal);
                if (!alreadyCounted) {
                    repository.insertBoardCut(
                            ev.eventUid(),
                            machineId,
                            machineName,
                            orderId != null
                                    ? orderId
                                    : (live != null && live.get("current_order_id") instanceof Number n
                                            ? n.longValue()
                                            : null),
                            jobName,
                            boardsDelta,
                            boardsSessionTotal,
                            eventTime,
                            "EVENT");
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
        boolean newCutWindow = machine.get("job_started_at") == null;
        repository.markJobStarted(machineId, orderId, started);

        // CORTE_INICIO en cada ventana (primera PRODUCCION o reanudación tras CORTE_FIN).
        if (changed || newCutWindow) {
            obrasClient.registrarTrazabilidad(
                    op,
                    orderId,
                    orderName,
                    "PRODUCCION",
                    "CORTE_INICIO",
                    "Inicio de corte ("
                            + source
                            + ") seccionador="
                            + str(machine.get("machine_name"))
                            + " job="
                            + str(machine.get("job_name"))
                            + " t="
                            + eventTime,
                    0,
                    intOrZero(order.get("partes_totales")),
                    "AGENTE:" + str(machine.get("machine_name")));
            if (changed) {
                log.info("Obra {} → PRODUCCION (fuente={})", orderName, source);
                try {
                    fulfillmentService.onObraProduccion(orderName, str(order.get("bookingcode")));
                } catch (Exception ex) {
                    log.warn(
                            "No se pudo avanzar proyecto a PRODUCCION por obra {}: {}",
                            orderName,
                            ex.getMessage());
                }
            }
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
                        + ") seccionador="
                        + str(machine.get("machine_name"))
                        + " duración="
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
        return buildLabelForPart(
                machineId, machineName, order, osiPart, eventUid, printLocal, null, null);
    }

    private LabelDto buildLabelForPart(
            int machineId,
            String machineName,
            Map<String, Object> order,
            String osiPart,
            String eventUid,
            boolean printLocal,
            Integer pieceOverride,
            String unitCodeOverride) {
        long orderId = ((Number) order.get("orderid")).longValue();
        String orderName = str(order.get("ordername"));
        Map<String, Object> mapped =
                obrasClient.partForOsi(orderId, osiPart, machineName, pieceOverride, unitCodeOverride);
        if (mapped == null) {
            // ERP caído: registrar corte UNMAPPED para sync/backfill y no perder el avance visual.
            String fallbackUnit = orderName + "-" + osiPart.replaceAll("\\s+", "");
            long cutId =
                    repository.insertCutPiece(
                            eventUid,
                            machineId,
                            orderId,
                            orderName,
                            null,
                            osiPart,
                            fallbackUnit,
                            "UNMAPPED",
                            null);
            log.warn(
                    "partForOsi sin respuesta — corte UNMAPPED machine={} order={} part='{}'",
                    machineId,
                    orderId,
                    osiPart);
            return new LabelDto(cutId, eventUid, osiPart, fallbackUnit, "UNMAPPED", null, printLocal);
        }
        String mapStatus = str(mapped.get("mapStatus"));
        String unitCode = str(mapped.get("unitCode"));
        String zpl = str(mapped.get("zpl"));
        Long partId =
                mapped.get("partId") instanceof Number n ? n.longValue() : null;

        // UNMAPPED: solo cut_piece + sticker.
        // MAPPED: el corte queda en monitor (biesse_agent_cut_piece); el detalle de orden lo pinta desde ahí.
        long cutId =
                repository.insertCutPiece(
                        eventUid,
                        machineId,
                        orderId,
                        orderName,
                        partId,
                        osiPart,
                        unitCode,
                        mapStatus,
                        zpl);
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

    /**
     * Plancha = board OSI ({@code Boards done} / {@code boards_done}).
     * Fallback: si solo llega status (sin evento reciente), registra el incremento.
     */
    private void recordBoardsFromStatusDelta(
            int machineId,
            String machineName,
            String jobName,
            Long orderId,
            Integer previousBoards,
            Integer newBoards,
            Instant eventTime) {
        if (newBoards == null || previousBoards == null) {
            return;
        }
        if (newBoards <= previousBoards) {
            return;
        }
        // El evento Boards done es la fuente preferida; evitar doble conteo.
        if (repository.recentBoardsDoneEvent(machineId, 45)) {
            return;
        }
        int maxAfter = repository.maxBoardsTotalAfter(machineId, jobName);
        if (newBoards <= maxAfter) {
            return;
        }
        if (repository.boardCutExistsForTotal(machineId, jobName, newBoards)) {
            return;
        }
        int delta = newBoards - Math.max(previousBoards, maxAfter);
        if (delta <= 0) {
            return;
        }
        String syntheticUid =
                "status-boards-"
                        + machineId
                        + "-"
                        + (jobName != null ? jobName.replaceAll("\\s+", "_") : "noj")
                        + "-"
                        + newBoards;
        if (syntheticUid.length() > 64) {
            syntheticUid =
                    "status-boards-"
                            + machineId
                            + "-"
                            + Integer.toHexString(jobName != null ? jobName.hashCode() : 0)
                            + "-"
                            + newBoards;
        }
        repository.insertBoardCut(
                syntheticUid,
                machineId,
                machineName,
                orderId,
                jobName,
                delta,
                newBoards,
                eventTime,
                "STATUS_DELTA");
    }

    private static int parseBoardsDelta(String code) {
        if (code == null || code.isBlank()) {
            return 1;
        }
        try {
            int n = Integer.parseInt(code.trim());
            return Math.max(n, 1);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static Integer intOrNull(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
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

    /** Clave estable para uid/idempotencia: "P51" desde "Part P51 692×394 …". */
    private static String osiPartKey(String lastPart) {
        if (lastPart == null || lastPart.isBlank()) {
            return "0";
        }
        Matcher m = OSI_PART_KEY.matcher(lastPart.trim());
        if (m.find()) {
            return m.group(1).toUpperCase(Locale.ROOT);
        }
        return Integer.toHexString(lastPart.toLowerCase(Locale.ROOT).hashCode());
    }
}
