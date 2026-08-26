package com.allcenter.modulebiesse.agent;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BiesseAgentRepository {

    private static final Pattern OP_PATTERN = Pattern.compile("^([A-Za-z]?\\d{3,})\\b");
    private static final Pattern PART_PATTERN =
            Pattern.compile("(?i)^Part\\s*(P?\\d+)", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter EVENT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final JdbcTemplate jdbc;

    public Map<String, Object> findMachineById(int machineId) {
        List<Map<String, Object>> rows =
                jdbc.queryForList("SELECT * FROM biesse_agent_machine WHERE machine_id = ?", machineId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void updateHeartbeat(int machineId, BiesseAgentDtos.HeartbeatRequest req) {
        jdbc.update(
                """
                UPDATE biesse_agent_machine SET
                    online = TRUE,
                    agent_version = COALESCE(?, agent_version),
                    compatible_profile = COALESCE(?, compatible_profile),
                    printer_name = COALESCE(?, printer_name),
                    printer_enabled = COALESCE(?, printer_enabled),
                    plant_name = COALESCE(?, plant_name),
                    hostname = COALESCE(?, hostname),
                    log_path = COALESCE(?, log_path),
                    pending_queue_size = COALESCE(?, pending_queue_size),
                    log_byte_offset = COALESCE(?, log_byte_offset),
                    health_status = COALESCE(?, health_status),
                    last_error = ?,
                    last_heartbeat_at = CURRENT_TIMESTAMP
                WHERE machine_id = ?
                """,
                req.agentVersion(),
                req.compatibleProfile(),
                req.printerName(),
                req.printerEnabled(),
                req.plantName(),
                req.hostname(),
                req.logPath(),
                req.pendingQueueSize(),
                req.logByteOffset(),
                req.healthStatus(),
                req.lastError(),
                machineId);
    }

    public void updateStatus(int machineId, BiesseAgentDtos.StatusPayload status, Long orderId) {
        jdbc.update(
                """
                UPDATE biesse_agent_machine SET
                    online = TRUE,
                    state = ?,
                    job_name = ?,
                    pattern_name = ?,
                    last_part = ?,
                    boards_done = ?,
                    pieces_produced = ?,
                    osi_session_id = ?,
                    pending_queue_size = COALESCE(?, pending_queue_size),
                    log_byte_offset = COALESCE(?, log_byte_offset),
                    health_status = COALESCE(?, health_status),
                    current_order_id = COALESCE(?, current_order_id),
                    last_status_at = CURRENT_TIMESTAMP
                WHERE machine_id = ?
                """,
                status.state(),
                status.jobName(),
                status.patternName(),
                status.lastPart(),
                status.boardsDone(),
                status.piecesProduced(),
                status.osiSessionId(),
                status.pendingQueueSize(),
                status.logByteOffset(),
                status.healthStatus(),
                orderId,
                machineId);
    }

    public void markJobStarted(int machineId, Long orderId, Instant startedAt) {
        jdbc.update(
                """
                UPDATE biesse_agent_machine
                SET current_order_id = ?,
                    job_started_at = COALESCE(job_started_at, ?)
                WHERE machine_id = ?
                """,
                orderId,
                Timestamp.from(startedAt),
                machineId);
    }

    public void clearJobStarted(int machineId) {
        jdbc.update(
                "UPDATE biesse_agent_machine SET job_started_at = NULL WHERE machine_id = ?",
                machineId);
    }

    public boolean eventExists(String eventUid) {
        Integer n =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM biesse_agent_event WHERE event_uid = ?",
                        Integer.class,
                        eventUid);
        return n != null && n > 0;
    }

    public void insertEvent(
            String eventUid,
            int machineId,
            String type,
            String code,
            String description,
            String severity,
            Instant eventTime,
            Long orderId,
            String action) {
        jdbc.update(
                """
                INSERT INTO biesse_agent_event
                    (event_uid, machine_id, event_type, code, description, severity,
                     event_time, order_id, processed_action)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_uid) DO NOTHING
                """,
                eventUid,
                machineId,
                type,
                code,
                description,
                severity,
                eventTime != null ? Timestamp.from(eventTime) : null,
                orderId,
                action);
    }

    public long insertCutPiece(
            String eventUid,
            int machineId,
            Long orderId,
            Long partId,
            String osiPartId,
            String unitCode,
            String mapStatus,
            String zpl) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(
                con -> {
                    var ps =
                            con.prepareStatement(
                                    """
                                    INSERT INTO biesse_agent_cut_piece
                                        (event_uid, machine_id, order_id, part_id, osi_part_id,
                                         unit_code, map_status, zpl)
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                                    ON CONFLICT (event_uid) DO UPDATE SET zpl = EXCLUDED.zpl
                                    RETURNING cut_piece_id
                                    """,
                                    new String[] {"cut_piece_id"});
                    ps.setString(1, eventUid);
                    ps.setInt(2, machineId);
                    if (orderId == null) {
                        ps.setObject(3, null);
                    } else {
                        ps.setLong(3, orderId);
                    }
                    if (partId == null) {
                        ps.setObject(4, null);
                    } else {
                        ps.setLong(4, partId);
                    }
                    ps.setString(5, osiPartId);
                    ps.setString(6, unitCode);
                    ps.setString(7, mapStatus);
                    ps.setString(8, zpl);
                    return ps;
                },
                keys);
        Number key = keys.getKey();
        if (key != null) {
            return key.longValue();
        }
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "SELECT cut_piece_id FROM biesse_agent_cut_piece WHERE event_uid = ?",
                        eventUid);
        return rows.isEmpty() ? 0L : ((Number) rows.getFirst().get("cut_piece_id")).longValue();
    }

    public void ackPrint(Long cutPieceId, String eventUid, boolean printed, String error) {
        if (cutPieceId != null) {
            jdbc.update(
                    """
                    UPDATE biesse_agent_cut_piece
                    SET printed = ?, print_error = ?, printed_at = CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE printed_at END
                    WHERE cut_piece_id = ?
                    """,
                    printed,
                    error,
                    printed,
                    cutPieceId);
            return;
        }
        if (eventUid != null && !eventUid.isBlank()) {
            jdbc.update(
                    """
                    UPDATE biesse_agent_cut_piece
                    SET printed = ?, print_error = ?, printed_at = CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE printed_at END
                    WHERE event_uid = ?
                    """,
                    printed,
                    error,
                    printed,
                    eventUid);
        }
    }

    public Map<String, Object> findOrderForJob(String jobName) {
        if (jobName == null || jobName.isBlank()) {
            return null;
        }
        String token = jobName.trim();
        String op = extractOp(token);

        List<Map<String, Object>> exact =
                jdbc.queryForList(
                        """
                        SELECT orderid, ordername, bookingcode, op_codigo, estado_escaneo,
                               nparts, partes_totales
                        FROM ordenes
                        WHERE UPPER(TRIM(ordername)) = UPPER(TRIM(?))
                           OR (bookingcode IS NOT NULL AND UPPER(TRIM(bookingcode)) = UPPER(TRIM(?)))
                        ORDER BY fechacreacion DESC
                        LIMIT 1
                        """,
                        token,
                        token);
        if (!exact.isEmpty()) {
            return exact.getFirst();
        }

        if (op != null) {
            List<Map<String, Object>> byOp =
                    jdbc.queryForList(
                            """
                            SELECT orderid, ordername, bookingcode, op_codigo, estado_escaneo,
                                   nparts, partes_totales
                            FROM ordenes
                            WHERE UPPER(TRIM(COALESCE(op_codigo, ''))) = UPPER(?)
                               OR UPPER(TRIM(ordername)) LIKE UPPER(?) || ' %'
                               OR UPPER(REPLACE(ordername, ' ', '')) LIKE UPPER(REPLACE(?, ' ', '')) || '%'
                            ORDER BY fechacreacion DESC
                            LIMIT 1
                            """,
                            op,
                            op,
                            token);
            if (!byOp.isEmpty()) {
                return byOp.getFirst();
            }
        }

        String noSpaces = token.replaceAll("\\s+", "");
        List<Map<String, Object>> fuzzy =
                jdbc.queryForList(
                        """
                        SELECT orderid, ordername, bookingcode, op_codigo, estado_escaneo,
                               nparts, partes_totales
                        FROM ordenes
                        WHERE UPPER(REPLACE(ordername, ' ', '')) = UPPER(?)
                           OR UPPER(REPLACE(ordername, ' ', '')) LIKE UPPER(?) || '%'
                           OR UPPER(?) LIKE UPPER(REPLACE(ordername, ' ', '')) || '%'
                        ORDER BY fechacreacion DESC
                        LIMIT 1
                        """,
                        noSpaces,
                        noSpaces,
                        noSpaces);
        return fuzzy.isEmpty() ? null : fuzzy.getFirst();
    }

    public boolean markOrderProduccion(long orderId) {
        int updated =
                jdbc.update(
                        """
                        UPDATE ordenes
                        SET estado_escaneo = 'PRODUCCION',
                            fecha_modificacion = CURRENT_TIMESTAMP
                        WHERE orderid = ?
                          AND COALESCE(UPPER(estado_escaneo), '') NOT IN ('COMPLETADA', 'PRODUCCION')
                        """,
                        orderId);
        return updated > 0;
    }

    public void forceEstado(long orderId, String estado) {
        jdbc.update(
                """
                UPDATE ordenes
                SET estado_escaneo = ?,
                    fecha_modificacion = CURRENT_TIMESTAMP
                WHERE orderid = ?
                  AND COALESCE(UPPER(estado_escaneo), '') <> 'COMPLETADA'
                """,
                estado,
                orderId);
    }

    public void registrarTrazabilidad(
            String opCodigo,
            Long orderId,
            String orderName,
            String estado,
            String accion,
            String detalle,
            int piezas,
            int partes,
            String usuario) {
        jdbc.update(
                """
                INSERT INTO op_trazabilidad
                    (op_codigo, orderid, ordername, estado, accion, detalle,
                     piezas_totales, partes_totales, usuario, fecha)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                opCodigo != null ? opCodigo : extractOp(orderName),
                orderId,
                orderName,
                estado,
                accion,
                detalle,
                piezas,
                partes,
                usuario);
    }

    public Map<String, Object> findPartForOsi(long orderId, String osiPartText) {
        Integer partNumber = parsePartNumber(osiPartText);
        if (partNumber == null) {
            return null;
        }
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        """
                        SELECT partid, orderid, partcode, partnumber, cantidad, material,
                               descripcion, descripcion1, escaneado
                        FROM partes
                        WHERE orderid = ?
                          AND (partnumber = ? OR UPPER(TRIM(partcode)) = UPPER(?) OR UPPER(TRIM(partcode)) = UPPER(?))
                        ORDER BY partid
                        LIMIT 1
                        """,
                        orderId,
                        partNumber,
                        "P" + partNumber,
                        String.valueOf(partNumber));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Integer nextPieceNumber(long partId) {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        """
                        SELECT COALESCE(MIN(numero_pieza), 1) AS n
                        FROM piezas
                        WHERE partid = ? AND escaneado = FALSE
                        """,
                        partId);
        if (rows.isEmpty() || rows.getFirst().get("n") == null) {
            return 1;
        }
        return ((Number) rows.getFirst().get("n")).intValue();
    }

    public static String extractOp(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Matcher m = OP_PATTERN.matcher(name.trim());
        return m.find() ? m.group(1).toUpperCase() : null;
    }

    public static Integer parsePartNumber(String osiPartText) {
        if (osiPartText == null || osiPartText.isBlank()) {
            return null;
        }
        Matcher m = PART_PATTERN.matcher(osiPartText.trim());
        if (!m.find()) {
            return null;
        }
        String raw = m.group(1).toUpperCase().replace("P", "");
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Instant parseEventTime(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(value.trim(), EVENT_TIME);
            return ldt.atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(value.trim());
            } catch (Exception ignored) {
                return Instant.now();
            }
        }
    }

    public List<Map<String, Object>> listMachines() {
        return jdbc.queryForList(
                """
                SELECT machine_id, machine_name, company_id, online, state, job_name, pattern_name,
                       last_part, boards_done, pieces_produced, osi_session_id,
                       printer_name, printer_enabled, plant_name, hostname,
                       health_status, last_error, agent_version, compatible_profile,
                       current_order_id, job_started_at, last_heartbeat_at, last_status_at, created_at
                FROM biesse_agent_machine
                ORDER BY online DESC NULLS LAST, last_heartbeat_at DESC NULLS LAST, machine_id
                """);
    }

    public Map<String, Object> createMachine(String machineName, String plantName, String tokenHash) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(
                con -> {
                    var ps =
                            con.prepareStatement(
                                    """
                                    INSERT INTO biesse_agent_machine
                                        (machine_name, token_hash, plant_name, online, health_status)
                                    VALUES (?, ?, ?, FALSE, 'OK')
                                    RETURNING machine_id
                                    """,
                                    new String[] {"machine_id"});
                    ps.setString(1, machineName);
                    ps.setString(2, tokenHash);
                    ps.setString(3, plantName);
                    return ps;
                },
                keys);
        Number id = keys.getKey();
        long machineId = id != null ? id.longValue() : 0L;
        Map<String, Object> row = findMachineById((int) machineId);
        return row;
    }

    public boolean rotateToken(int machineId, String tokenHash) {
        int n =
                jdbc.update(
                        "UPDATE biesse_agent_machine SET token_hash = ? WHERE machine_id = ?",
                        tokenHash,
                        machineId);
        return n > 0;
    }

    public List<Map<String, Object>> listRecentEvents(int limit) {
        int safe = Math.max(1, Math.min(limit, 500));
        return jdbc.queryForList(
                """
                SELECT e.id, e.event_uid, e.machine_id, m.machine_name, e.event_type, e.code,
                       e.description, e.severity, e.event_time, e.order_id, e.processed_action,
                       e.created_at, o.ordername
                FROM biesse_agent_event e
                LEFT JOIN biesse_agent_machine m ON m.machine_id = e.machine_id
                LEFT JOIN ordenes o ON o.orderid = e.order_id
                ORDER BY e.created_at DESC, e.id DESC
                LIMIT ?
                """,
                safe);
    }

    public List<Map<String, Object>> listTrazabilidad(String opCodigo, Long orderId, int limit) {
        int safe = Math.max(1, Math.min(limit, 500));
        String op = opCodigo;
        if ((op == null || op.isBlank()) && orderId != null) {
            List<Map<String, Object>> ord =
                    jdbc.queryForList(
                            "SELECT ordername, op_codigo FROM ordenes WHERE orderid = ?", orderId);
            if (!ord.isEmpty()) {
                op = str(ord.getFirst().get("op_codigo"));
                if (op == null || op.isBlank()) {
                    op = extractOp(str(ord.getFirst().get("ordername")));
                }
            }
        }
        if (orderId != null && op != null && !op.isBlank()) {
            return jdbc.queryForList(
                    """
                    SELECT id, op_codigo, orderid, ordername, estado, accion, detalle,
                           xml_file, piezas_totales, partes_totales, usuario, usuario_id, fecha
                    FROM op_trazabilidad
                    WHERE orderid = ? OR UPPER(TRIM(op_codigo)) = UPPER(TRIM(?))
                    ORDER BY fecha DESC, id DESC
                    LIMIT ?
                    """,
                    orderId,
                    op,
                    safe);
        }
        if (orderId != null) {
            return jdbc.queryForList(
                    """
                    SELECT id, op_codigo, orderid, ordername, estado, accion, detalle,
                           xml_file, piezas_totales, partes_totales, usuario, usuario_id, fecha
                    FROM op_trazabilidad
                    WHERE orderid = ?
                    ORDER BY fecha DESC, id DESC
                    LIMIT ?
                    """,
                    orderId,
                    safe);
        }
        if (op != null && !op.isBlank()) {
            return jdbc.queryForList(
                    """
                    SELECT id, op_codigo, orderid, ordername, estado, accion, detalle,
                           xml_file, piezas_totales, partes_totales, usuario, usuario_id, fecha
                    FROM op_trazabilidad
                    WHERE UPPER(TRIM(op_codigo)) = UPPER(TRIM(?))
                    ORDER BY fecha DESC, id DESC
                    LIMIT ?
                    """,
                    op.trim(),
                    safe);
        }
        return jdbc.queryForList(
                """
                SELECT id, op_codigo, orderid, ordername, estado, accion, detalle,
                       xml_file, piezas_totales, partes_totales, usuario, usuario_id, fecha
                FROM op_trazabilidad
                ORDER BY fecha DESC, id DESC
                LIMIT ?
                """,
                safe);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    public List<Map<String, Object>> listRecentCutPieces(int limit) {
        int safe = Math.max(1, Math.min(limit, 200));
        return jdbc.queryForList(
                """
                SELECT c.cut_piece_id, c.event_uid, c.machine_id, c.order_id, c.part_id,
                       c.osi_part_id, c.unit_code, c.map_status, c.printed, c.print_error,
                       c.printed_at, c.created_at, o.ordername, m.machine_name
                FROM biesse_agent_cut_piece c
                LEFT JOIN ordenes o ON o.orderid = c.order_id
                LEFT JOIN biesse_agent_machine m ON m.machine_id = c.machine_id
                ORDER BY c.created_at DESC
                LIMIT ?
                """,
                safe);
    }
}
