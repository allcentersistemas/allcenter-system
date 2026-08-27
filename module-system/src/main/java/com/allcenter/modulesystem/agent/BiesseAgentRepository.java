package com.allcenter.modulesystem.agent;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** Persistencia del agente seccionador en {@code app_db} (máquinas, eventos, cortes). */
@Repository
@RequiredArgsConstructor
public class BiesseAgentRepository {

    private static final Pattern OP_PATTERN = Pattern.compile("^([A-Za-z]?\\d{3,})\\b");
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
        // UNKNOWN/vacío no pisa un estado OSI conocido (IDLE/RUN/…).
        String rawState = status.state();
        String stateParam =
                rawState == null
                                || rawState.isBlank()
                                || "UNKNOWN".equalsIgnoreCase(rawState.trim())
                        ? null
                        : rawState.trim();
        jdbc.update(
                """
                UPDATE biesse_agent_machine SET
                    online = TRUE,
                    state = COALESCE(?, state),
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
                stateParam,
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

    /** Evita doble corte status+evento para la misma pieza OSI reciente. */
    public boolean hasRecentCutForOsi(int machineId, Long orderId, String osiPart, int withinSeconds) {
        if (osiPart == null || osiPart.isBlank()) {
            return false;
        }
        String token = osiPart.trim();
        // Normalizar a "Part P7" / "Part 7" para comparar prefijo.
        int cut = token.indexOf(' ');
        if (cut > 0) {
            int second = token.indexOf(' ', cut + 1);
            if (second > 0) {
                token = token.substring(0, second).trim(); // "Part P7"
            }
        }
        int safeSecs = Math.max(30, Math.min(withinSeconds, 3600));
        Integer n =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM biesse_agent_cut_piece
                        WHERE machine_id = ?
                          AND (?::bigint IS NULL OR order_id = ?)
                          AND osi_part_id IS NOT NULL
                          AND UPPER(TRIM(osi_part_id)) LIKE UPPER(?) || '%'
                          AND created_at >= CURRENT_TIMESTAMP - (? || ' seconds')::interval
                        """,
                        Integer.class,
                        machineId,
                        orderId,
                        orderId,
                        token,
                        String.valueOf(safeSecs));
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
            String orderName,
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
                                        (event_uid, machine_id, order_id, order_name, part_id, osi_part_id,
                                         unit_code, map_status, zpl)
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                                    ON CONFLICT (event_uid) DO UPDATE SET
                                        zpl = EXCLUDED.zpl,
                                        order_name = COALESCE(EXCLUDED.order_name, biesse_agent_cut_piece.order_name)
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
                    ps.setString(4, orderName);
                    if (partId == null) {
                        ps.setObject(5, null);
                    } else {
                        ps.setLong(5, partId);
                    }
                    ps.setString(6, osiPartId);
                    ps.setString(7, unitCode);
                    ps.setString(8, mapStatus);
                    ps.setString(9, zpl);
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

    public static String extractOp(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Matcher m = OP_PATTERN.matcher(name.trim());
        return m.find() ? m.group(1).toUpperCase() : null;
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

    /**
     * Segundos sin heartbeat/status para considerar la máquina offline.
     * El agente late cada 5–10s, pero al subir eventos/status (timeouts ~8s) o con
     * backoff de red puede pasar de 30s sin señal aunque siga conectado.
     */
    public static final int ONLINE_STALE_SECONDS = 90;

    /** Última señal de vida: el más reciente entre heartbeat y status. */
    private static final String LAST_SEEN_SQL =
            """
            CASE
              WHEN last_heartbeat_at IS NULL THEN last_status_at
              WHEN last_status_at IS NULL THEN last_heartbeat_at
              WHEN last_heartbeat_at >= last_status_at THEN last_heartbeat_at
              ELSE last_status_at
            END
            """;

    /**
     * Persiste {@code online=FALSE} cuando el último heartbeat/status es más viejo que el TTL.
     * Así el flag no queda “pegado” en TRUE tras desconexión.
     */
    public int markStaleMachinesOffline() {
        return jdbc.update(
                """
                UPDATE biesse_agent_machine SET online = FALSE
                WHERE online = TRUE
                  AND (
                    ("""
                        + LAST_SEEN_SQL
                        + """
                    ) IS NULL
                    OR ("""
                        + LAST_SEEN_SQL
                        + """
                    ) < CURRENT_TIMESTAMP - (? * INTERVAL '1 second')
                  )
                """,
                ONLINE_STALE_SECONDS);
    }

    public List<Map<String, Object>> listMachines() {
        markStaleMachinesOffline();
        return jdbc.queryForList(
                """
                SELECT machine_id, machine_name, company_id,
                       (
                         ("""
                        + LAST_SEEN_SQL
                        + """
                         ) IS NOT NULL
                         AND ("""
                        + LAST_SEEN_SQL
                        + """
                         ) > CURRENT_TIMESTAMP - (? * INTERVAL '1 second')
                       ) AS online,
                       state, job_name, pattern_name,
                       last_part, boards_done, pieces_produced, osi_session_id,
                       printer_name, printer_enabled, plant_name, hostname, machine_type,
                       health_status, last_error, agent_version, compatible_profile,
                       current_order_id, job_started_at, last_heartbeat_at, last_status_at, created_at
                FROM biesse_agent_machine
                ORDER BY machine_id ASC
                """,
                ONLINE_STALE_SECONDS);
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
        return findMachineById((int) machineId);
    }

    public boolean rotateToken(int machineId, String tokenHash) {
        int n =
                jdbc.update(
                        "UPDATE biesse_agent_machine SET token_hash = ? WHERE machine_id = ?",
                        tokenHash,
                        machineId);
        return n > 0;
    }

    /**
     * Elimina el seccionador y datos asociados. {@code biesse_agent_cut_piece} no tiene FK CASCADE.
     *
     * @return true si existía y se eliminó
     */
    public boolean deleteMachine(int machineId) {
        if (findMachineById(machineId) == null) {
            return false;
        }
        jdbc.update("DELETE FROM biesse_agent_cut_piece WHERE machine_id = ?", machineId);
        int n = jdbc.update("DELETE FROM biesse_agent_machine WHERE machine_id = ?", machineId);
        return n > 0;
    }

    public List<Map<String, Object>> listRecentEvents(int limit) {
        int safe = Math.max(1, Math.min(limit, 500));
        return jdbc.queryForList(
                """
                SELECT e.id, e.event_uid, e.machine_id, m.machine_name, e.event_type, e.code,
                       e.description, e.severity, e.event_time, e.order_id, e.processed_action,
                       e.created_at
                FROM biesse_agent_event e
                LEFT JOIN biesse_agent_machine m ON m.machine_id = e.machine_id
                ORDER BY e.created_at DESC, e.id DESC
                LIMIT ?
                """,
                safe);
    }

    public List<Map<String, Object>> listRecentCutPieces(int limit) {
        return listCutPieces(null, limit);
    }

    public List<Map<String, Object>> listCutPieces(Long orderId, int limit) {
        int safe = Math.max(1, Math.min(limit, 500));
        if (orderId != null) {
            return jdbc.queryForList(
                    """
                    SELECT c.cut_piece_id, c.event_uid, c.machine_id, c.order_id, c.order_name,
                           c.part_id, c.osi_part_id, c.unit_code, c.map_status, c.printed, c.print_error,
                           c.printed_at, c.created_at, m.machine_name, m.plant_name
                    FROM biesse_agent_cut_piece c
                    LEFT JOIN biesse_agent_machine m ON m.machine_id = c.machine_id
                    WHERE c.order_id = ?
                    ORDER BY c.created_at DESC
                    LIMIT ?
                    """,
                    orderId,
                    safe);
        }
        return jdbc.queryForList(
                """
                SELECT c.cut_piece_id, c.event_uid, c.machine_id, c.order_id, c.order_name,
                       c.part_id, c.osi_part_id, c.unit_code, c.map_status, c.printed, c.print_error,
                       c.printed_at, c.created_at, m.machine_name, m.plant_name
                FROM biesse_agent_cut_piece c
                LEFT JOIN biesse_agent_machine m ON m.machine_id = c.machine_id
                ORDER BY c.created_at DESC
                LIMIT ?
                """,
                safe);
    }

    /** Ventanas de corte derivadas de CORTE_INICIO / CORTE_FIN en eventos del agente. */
    public List<Map<String, Object>> listRecentCutWindows(int limit) {
        int safe = Math.max(1, Math.min(limit, 200));
        return jdbc.queryForList(
                """
                SELECT e.id, e.event_uid, e.machine_id, m.machine_name, m.plant_name,
                       e.event_type, e.description, e.event_time, e.order_id, e.processed_action,
                       e.created_at
                FROM biesse_agent_event e
                LEFT JOIN biesse_agent_machine m ON m.machine_id = e.machine_id
                WHERE e.processed_action IN ('PRODUCCION', 'CORTE_FIN', 'LABEL', 'BOARDS_DONE')
                   OR UPPER(COALESCE(e.processed_action, '')) LIKE 'CORTE_%'
                ORDER BY e.created_at DESC, e.id DESC
                LIMIT ?
                """,
                safe);
    }

    /**
     * Registra planchas cortadas (idempotente por {@code event_uid}).
     *
     * @return true si se insertó una fila nueva
     */
    public boolean insertBoardCut(
            String eventUid,
            int machineId,
            String machineName,
            Long orderId,
            String jobName,
            int boardsDelta,
            Integer boardsTotalAfter,
            Instant eventTime,
            String source) {
        if (eventUid == null || eventUid.isBlank() || boardsDelta <= 0) {
            return false;
        }
        int n =
                jdbc.update(
                        """
                        INSERT INTO biesse_agent_board_cut
                            (machine_id, machine_name, order_id, job_name, boards_delta,
                             boards_total_after, event_uid, event_time, source)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (event_uid) DO NOTHING
                        """,
                        machineId,
                        machineName,
                        orderId,
                        jobName,
                        boardsDelta,
                        boardsTotalAfter,
                        eventUid.trim(),
                        eventTime != null ? Timestamp.from(eventTime) : Timestamp.from(Instant.now()),
                        source != null ? source : "EVENT");
        return n > 0;
    }

    /** Evita doble conteo status vs evento cuando ya hay un registro con el mismo total. */
    public boolean boardCutExistsForTotal(int machineId, String jobName, int boardsTotalAfter) {
        Integer n =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM biesse_agent_board_cut
                        WHERE machine_id = ?
                          AND boards_total_after = ?
                          AND COALESCE(job_name, '') = COALESCE(?, '')
                          AND COALESCE(event_time, created_at) >= CURRENT_TIMESTAMP - INTERVAL '36 hours'
                        """,
                        Integer.class,
                        machineId,
                        boardsTotalAfter,
                        jobName);
        return n != null && n > 0;
    }

    public String boardCutSourceForTotal(int machineId, String jobName, int boardsTotalAfter) {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        """
                        SELECT source FROM biesse_agent_board_cut
                        WHERE machine_id = ?
                          AND boards_total_after = ?
                          AND COALESCE(job_name, '') = COALESCE(?, '')
                          AND COALESCE(event_time, created_at) >= CURRENT_TIMESTAMP - INTERVAL '36 hours'
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """,
                        machineId,
                        boardsTotalAfter,
                        jobName);
        if (rows.isEmpty() || rows.getFirst().get("source") == null) {
            return null;
        }
        return String.valueOf(rows.getFirst().get("source"));
    }

    public int maxBoardsTotalAfter(int machineId, String jobName) {
        Integer n =
                jdbc.queryForObject(
                        """
                        SELECT COALESCE(MAX(boards_total_after), 0) FROM biesse_agent_board_cut
                        WHERE machine_id = ?
                          AND COALESCE(job_name, '') = COALESCE(?, '')
                          AND COALESCE(event_time, created_at) >= CURRENT_TIMESTAMP - INTERVAL '36 hours'
                        """,
                        Integer.class,
                        machineId,
                        jobName);
        return n != null ? n : 0;
    }

    public boolean recentBoardsDoneEvent(int machineId, int withinSeconds) {
        int safe = Math.max(5, Math.min(withinSeconds, 300));
        Integer n =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM biesse_agent_event
                        WHERE machine_id = ?
                          AND LOWER(COALESCE(event_type, '')) = 'boards done'
                          AND created_at >= CURRENT_TIMESTAMP - (? * INTERVAL '1 second')
                        """,
                        Integer.class,
                        machineId,
                        safe);
        return n != null && n > 0;
    }

    public int sumBoardsToday(Integer machineId) {
        if (machineId != null) {
            Integer n =
                    jdbc.queryForObject(
                            """
                            SELECT COALESCE(SUM(boards_delta), 0) FROM biesse_agent_board_cut
                            WHERE machine_id = ?
                              AND DATE(COALESCE(event_time, created_at)) = CURRENT_DATE
                            """,
                            Integer.class,
                            machineId);
            return n != null ? n : 0;
        }
        Integer n =
                jdbc.queryForObject(
                        """
                        SELECT COALESCE(SUM(boards_delta), 0) FROM biesse_agent_board_cut
                        WHERE DATE(COALESCE(event_time, created_at)) = CURRENT_DATE
                        """,
                        Integer.class);
        return n != null ? n : 0;
    }

    public List<Map<String, Object>> listBoardCuts(
            LocalDate from, LocalDate to, Integer machineId, int limit) {
        int safe = Math.max(1, Math.min(limit, 500));
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT b.id, b.machine_id, b.machine_name, b.order_id, b.job_name,
                               b.boards_delta, b.boards_total_after, b.event_uid, b.event_time,
                               b.source, b.created_at
                        FROM biesse_agent_board_cut b
                        WHERE 1=1
                        """);
        List<Object> args = new ArrayList<>();
        if (from != null) {
            sql.append(" AND DATE(COALESCE(b.event_time, b.created_at)) >= ?");
            args.add(Date.valueOf(from));
        }
        if (to != null) {
            sql.append(" AND DATE(COALESCE(b.event_time, b.created_at)) <= ?");
            args.add(Date.valueOf(to));
        }
        if (machineId != null) {
            sql.append(" AND b.machine_id = ?");
            args.add(machineId);
        }
        sql.append(" ORDER BY COALESCE(b.event_time, b.created_at) DESC, b.id DESC LIMIT ?");
        args.add(safe);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> summarizeBoardCuts(LocalDate from, LocalDate to) {
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT b.machine_id,
                               COALESCE(MAX(b.machine_name), m.machine_name) AS machine_name,
                               COALESCE(SUM(b.boards_delta), 0) AS boards_total,
                               COUNT(*) AS cut_events
                        FROM biesse_agent_board_cut b
                        LEFT JOIN biesse_agent_machine m ON m.machine_id = b.machine_id
                        WHERE 1=1
                        """);
        List<Object> args = new ArrayList<>();
        if (from != null) {
            sql.append(" AND DATE(COALESCE(b.event_time, b.created_at)) >= ?");
            args.add(Date.valueOf(from));
        }
        if (to != null) {
            sql.append(" AND DATE(COALESCE(b.event_time, b.created_at)) <= ?");
            args.add(Date.valueOf(to));
        }
        sql.append(" GROUP BY b.machine_id, m.machine_name ORDER BY boards_total DESC, b.machine_id");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public int sumBoardCutsInRange(LocalDate from, LocalDate to, Integer machineId) {
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT COALESCE(SUM(boards_delta), 0) FROM biesse_agent_board_cut
                        WHERE 1=1
                        """);
        List<Object> args = new ArrayList<>();
        if (from != null) {
            sql.append(" AND DATE(COALESCE(event_time, created_at)) >= ?");
            args.add(Date.valueOf(from));
        }
        if (to != null) {
            sql.append(" AND DATE(COALESCE(event_time, created_at)) <= ?");
            args.add(Date.valueOf(to));
        }
        if (machineId != null) {
            sql.append(" AND machine_id = ?");
            args.add(machineId);
        }
        Integer n = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        return n != null ? n : 0;
    }
}
