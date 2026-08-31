package com.allcenter.modulebiesse.obras;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Schema de OP / trazabilidad / piezas.cortada en BD obras (sin tablas de agente). */
@Component
@RequiredArgsConstructor
public class BiesseObrasSchemaAligner {

    private static final Logger log = LoggerFactory.getLogger(BiesseObrasSchemaAligner.class);

    private final JdbcTemplate jdbc;

    @PostConstruct
    public void align() {
        try {
            ensureReady();
            log.info("Biesse obras schema (op/trazabilidad) OK");
        } catch (Exception e) {
            log.warn("Biesse obras schema align failed: {}", e.getMessage());
        }
    }

    public synchronized void ensureReady() {
        ensureOpCodigo();
        ensureOpTrazabilidad();
        ensurePiezasCortada();
        backfillOpCodigos();
    }

    /** Marca de corte vía agente (solo eventos nuevos; sin backfill histórico). */
    private void ensurePiezasCortada() {
        try {
            jdbc.execute("ALTER TABLE piezas ADD COLUMN IF NOT EXISTS cortada BOOLEAN DEFAULT FALSE");
            jdbc.execute("ALTER TABLE piezas ADD COLUMN IF NOT EXISTS cortada_at TIMESTAMP");
            jdbc.execute("ALTER TABLE piezas ADD COLUMN IF NOT EXISTS cortada_por VARCHAR(120)");
            jdbc.execute(
                    "CREATE INDEX IF NOT EXISTS idx_piezas_partid_cortada ON piezas(partid, cortada)");
            // Solo visual: error de captura agente (no cambia PRODUCCION / escaneo).
            jdbc.execute("ALTER TABLE piezas ADD COLUMN IF NOT EXISTS corte_error BOOLEAN DEFAULT FALSE");
            jdbc.execute("ALTER TABLE piezas ADD COLUMN IF NOT EXISTS corte_error_at TIMESTAMP");
            jdbc.execute("ALTER TABLE piezas ADD COLUMN IF NOT EXISTS corte_error_msg VARCHAR(240)");
        } catch (Exception e) {
            log.debug("ensure piezas.cortada (tabla ausente?): {}", e.getMessage());
        }
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
                long orderId = ((Number) row.get("orderid")).longValue();
                String name = row.get("ordername") != null ? String.valueOf(row.get("ordername")) : "";
                String op = BiesseObrasRepository.resolveOpCodigo(null, name, orderId);
                jdbc.update(
                        "UPDATE ordenes SET op_codigo = ? WHERE orderid = ?",
                        op,
                        orderId);
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
}
