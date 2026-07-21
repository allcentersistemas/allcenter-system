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
 * Columnas IA de {@code app_config}.
 * Hibernate {@code ddl-auto=update} no puede añadir {@code NOT NULL} sin default sobre filas existentes.
 */
@Component("appConfigSchemaAligner")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AppConfigSchemaAligner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AppConfigSchemaAligner.class);

    private final JdbcTemplate jdbc;

    public AppConfigSchemaAligner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        align();
    }

    void align() {
        if (!tableExists("app_config")) {
            return;
        }
        addColumnIfMissing("ai_vision_enabled", "BOOLEAN NOT NULL DEFAULT false");
        addColumnIfMissing("ai_provider", "VARCHAR(32) DEFAULT 'claude'");
        addColumnIfMissing("ai_model", "VARCHAR(80) DEFAULT ''");
        addColumnIfMissing("ai_api_key", "VARCHAR(512) DEFAULT ''");
        addColumnIfMissing("ai_daily_limit_per_client", "INTEGER NOT NULL DEFAULT 20");
        backfillNulls();
    }

    private void backfillNulls() {
        try {
            jdbc.update(
                    """
                    UPDATE app_config SET
                      ai_vision_enabled = COALESCE(ai_vision_enabled, false),
                      ai_provider = COALESCE(NULLIF(TRIM(ai_provider), ''), 'claude'),
                      ai_model = COALESCE(ai_model, ''),
                      ai_api_key = COALESCE(ai_api_key, ''),
                      ai_daily_limit_per_client = COALESCE(ai_daily_limit_per_client, 20)
                    WHERE id = 1
                    """);
        } catch (Exception ex) {
            log.debug("Backfill app_config IA omitido: {}", ex.getMessage());
        }
    }

    private void addColumnIfMissing(String column, String sqlType) {
        if (columnExists("app_config", column)) {
            return;
        }
        try {
            jdbc.execute("ALTER TABLE app_config ADD COLUMN " + column + " " + sqlType);
            log.info("app_config.{} creada", column);
        } catch (Exception ex) {
            log.warn("No se pudo crear app_config.{}: {}", column, ex.getMessage());
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
