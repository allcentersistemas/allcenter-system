package com.allcenter.modulebiesse.agent;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Asegura tablas del agente CNC + bootstrap opcional de máquina/token. */
@Component
@RequiredArgsConstructor
public class BiesseAgentSchemaAligner {

    private static final Logger log = LoggerFactory.getLogger(BiesseAgentSchemaAligner.class);

    private final JdbcTemplate jdbc;

    @Value("${app.biesse.agent.bootstrap-enabled:true}")
    private boolean bootstrapEnabled;

    @Value("${app.biesse.agent.bootstrap-token:dev-biesse-agent-token}")
    private String bootstrapToken;

    @Value("${app.biesse.agent.bootstrap-machine-name:BIESSE-OSI}")
    private String bootstrapMachineName;

    @PostConstruct
    public void align() {
        try {
            ensureReady();
            bootstrapMachineIfNeeded();
            log.info("Biesse agent schema OK");
        } catch (Exception e) {
            log.warn("Biesse agent schema align failed: {}", e.getMessage());
        }
    }

    /** Idempotente: crear tablas/columnas si faltan (útil tras deploy sin reinicio limpio). */
    public synchronized void ensureReady() {
        ensureOpCodigo();
        ensureOpTrazabilidad();
        ensureAgentTables();
        backfillOpCodigos();
    }

    private void ensureOpCodigo() {
        jdbc.execute("ALTER TABLE ordenes ADD COLUMN IF NOT EXISTS op_codigo VARCHAR(40)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_ordenes_op_codigo ON ordenes(op_codigo)");
    }

    private void backfillOpCodigos() {
        try {
            List<Map<String, Object>> rows =
                    jdbc.queryForList(
                            """
                            SELECT orderid, ordername FROM ordenes
                            WHERE op_codigo IS NULL OR TRIM(op_codigo) = ''
                            LIMIT 5000
                            """);
            for (Map<String, Object> row : rows) {
                String name = row.get("ordername") != null ? String.valueOf(row.get("ordername")) : "";
                String op = BiesseAgentRepository.extractOp(name);
                if (op == null) {
                    continue;
                }
                jdbc.update(
                        "UPDATE ordenes SET op_codigo = ? WHERE orderid = ?",
                        op,
                        ((Number) row.get("orderid")).longValue());
            }
        } catch (Exception e) {
            log.debug("backfill op_codigo: {}", e.getMessage());
        }
    }

    private void ensureOpTrazabilidad() {
        jdbc.execute(
                """
                CREATE TABLE IF NOT EXISTS op_trazabilidad
                (
                    id SERIAL PRIMARY KEY,
                    op_codigo VARCHAR(40) NOT NULL,
                    orderid INTEGER,
                    ordername TEXT,
                    estado VARCHAR(50) NOT NULL,
                    accion VARCHAR(80) NOT NULL,
                    detalle TEXT,
                    xml_file TEXT,
                    piezas_totales INTEGER DEFAULT 0,
                    partes_totales INTEGER DEFAULT 0,
                    usuario VARCHAR(120),
                    usuario_id INTEGER,
                    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute(
                "CREATE INDEX IF NOT EXISTS idx_op_trazabilidad_op_fecha "
                        + "ON op_trazabilidad(op_codigo, fecha DESC)");
    }

    private void ensureAgentTables() {
        jdbc.execute(
                """
                CREATE TABLE IF NOT EXISTS biesse_agent_machine
                (
                    machine_id SERIAL PRIMARY KEY,
                    machine_name VARCHAR(120) NOT NULL,
                    token_hash VARCHAR(64) NOT NULL UNIQUE,
                    company_id INTEGER,
                    online BOOLEAN DEFAULT FALSE,
                    state VARCHAR(40),
                    job_name TEXT,
                    pattern_name TEXT,
                    last_part TEXT,
                    boards_done INTEGER,
                    pieces_produced INTEGER,
                    osi_session_id TEXT,
                    printer_name TEXT,
                    printer_enabled BOOLEAN DEFAULT FALSE,
                    plant_name TEXT,
                    hostname TEXT,
                    log_path TEXT,
                    log_byte_offset BIGINT,
                    pending_queue_size INTEGER,
                    health_status VARCHAR(40),
                    last_error TEXT,
                    agent_version VARCHAR(40),
                    compatible_profile VARCHAR(80),
                    current_order_id INTEGER,
                    job_started_at TIMESTAMP,
                    last_heartbeat_at TIMESTAMP,
                    last_status_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute(
                """
                CREATE TABLE IF NOT EXISTS biesse_agent_event
                (
                    id BIGSERIAL PRIMARY KEY,
                    event_uid VARCHAR(64) NOT NULL UNIQUE,
                    machine_id INTEGER NOT NULL REFERENCES biesse_agent_machine(machine_id) ON DELETE CASCADE,
                    event_type VARCHAR(80),
                    code TEXT,
                    description TEXT,
                    severity VARCHAR(40),
                    event_time TIMESTAMP,
                    order_id INTEGER,
                    processed_action VARCHAR(80),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute(
                """
                CREATE TABLE IF NOT EXISTS biesse_agent_cut_piece
                (
                    cut_piece_id BIGSERIAL PRIMARY KEY,
                    event_uid VARCHAR(64) NOT NULL UNIQUE,
                    machine_id INTEGER NOT NULL,
                    order_id INTEGER,
                    part_id INTEGER,
                    osi_part_id TEXT,
                    unit_code TEXT,
                    map_status VARCHAR(40),
                    zpl TEXT,
                    printed BOOLEAN DEFAULT FALSE,
                    print_error TEXT,
                    printed_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute(
                "CREATE INDEX IF NOT EXISTS idx_biesse_agent_event_machine "
                        + "ON biesse_agent_event(machine_id, created_at DESC)");
    }

    private void bootstrapMachineIfNeeded() {
        if (!bootstrapEnabled || bootstrapToken == null || bootstrapToken.isBlank()) {
            return;
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM biesse_agent_machine", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        String hash = sha256Hex(bootstrapToken.trim());
        jdbc.update(
                """
                INSERT INTO biesse_agent_machine (machine_name, token_hash, online, health_status)
                VALUES (?, ?, FALSE, 'OK')
                """,
                bootstrapMachineName,
                hash);
        log.warn(
                "Máquina agente bootstrap creada name='{}' — use token de app.biesse.agent.bootstrap-token",
                bootstrapMachineName);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public Map<String, Object> findMachineByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "SELECT * FROM biesse_agent_machine WHERE token_hash = ?",
                        sha256Hex(rawToken.trim()));
        return rows.isEmpty() ? null : rows.getFirst();
    }
}
