package com.allcenter.modulesystem.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Garantiza tabla {@code planilla_ai_usage} y columnas (idempotente). Hibernate puede crear la
 * entidad; este aligner cubre despliegues con filas/esquema parcial.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PlanillaAiUsageSchemaAligner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlanillaAiUsageSchemaAligner.class);

    private final JdbcTemplate jdbc;

    public PlanillaAiUsageSchemaAligner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        align();
    }

    void align() {
        ensureTable();
        if (!tableExists("planilla_ai_usage")) {
            return;
        }
        addColumnIfMissing("client_user_id", "BIGINT NOT NULL DEFAULT 0");
        addColumnIfMissing("created_at", "TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()");
        addColumnIfMissing("provider", "VARCHAR(32) DEFAULT ''");
        addColumnIfMissing("model", "VARCHAR(80) DEFAULT ''");
        addColumnIfMissing("success", "BOOLEAN NOT NULL DEFAULT false");
        addColumnIfMissing("filas_count", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("input_tokens", "INTEGER");
        addColumnIfMissing("output_tokens", "INTEGER");
        addColumnIfMissing("reject_reason", "VARCHAR(1000)");
        addColumnIfMissing("original_filename", "VARCHAR(260)");
        addColumnIfMissing("bytes", "BIGINT");
        ensureIndex();
    }

    private void ensureTable() {
        if (tableExists("planilla_ai_usage")) {
            return;
        }
        try {
            jdbc.execute(
                    """
                    CREATE TABLE planilla_ai_usage (
                      id BIGSERIAL PRIMARY KEY,
                      client_user_id BIGINT NOT NULL,
                      created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                      provider VARCHAR(32) DEFAULT '',
                      model VARCHAR(80) DEFAULT '',
                      success BOOLEAN NOT NULL DEFAULT false,
                      filas_count INTEGER NOT NULL DEFAULT 0,
                      input_tokens INTEGER,
                      output_tokens INTEGER,
                      reject_reason VARCHAR(1000),
                      original_filename VARCHAR(260),
                      bytes BIGINT
                    )
                    """);
            log.info("Tabla planilla_ai_usage creada");
        } catch (Exception ex) {
            log.warn("No se pudo crear planilla_ai_usage: {}", ex.getMessage());
        }
    }

    private void ensureIndex() {
        try {
            jdbc.execute(
                    """
                    CREATE INDEX IF NOT EXISTS idx_planilla_ai_usage_client_created
                    ON planilla_ai_usage (client_user_id, created_at DESC)
                    """);
        } catch (Exception ex) {
            log.debug("Índice planilla_ai_usage omitido: {}", ex.getMessage());
        }
    }

    private void addColumnIfMissing(String column, String sqlType) {
        if (columnExists("planilla_ai_usage", column)) {
            return;
        }
        try {
            jdbc.execute("ALTER TABLE planilla_ai_usage ADD COLUMN " + column + " " + sqlType);
            log.info("planilla_ai_usage.{} creada", column);
        } catch (Exception ex) {
            log.warn("No se pudo crear planilla_ai_usage.{}: {}", column, ex.getMessage());
        }
    }

    private boolean tableExists(String table) {
        try {
            Integer n =
                    jdbc.queryForObject(
                            """
                            SELECT COUNT(*) FROM information_schema.tables
                            WHERE LOWER(table_schema) = 'public' AND LOWER(table_name) = LOWER(?)
                            """,
                            Integer.class,
                            table);
            return n != null && n > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean columnExists(String table, String column) {
        try {
            Integer n =
                    jdbc.queryForObject(
                            """
                            SELECT COUNT(*) FROM information_schema.columns
                            WHERE LOWER(table_schema) = 'public'
                              AND LOWER(table_name) = LOWER(?)
                              AND LOWER(column_name) = LOWER(?)
                            """,
                            Integer.class,
                            table,
                            column);
            return n != null && n > 0;
        } catch (Exception ex) {
            return false;
        }
    }
}
