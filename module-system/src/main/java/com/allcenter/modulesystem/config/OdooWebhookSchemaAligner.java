package com.allcenter.modulesystem.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OdooWebhookSchemaAligner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OdooWebhookSchemaAligner.class);

    private final JdbcTemplate jdbc;

    public OdooWebhookSchemaAligner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbc.execute(
                    """
                    CREATE TABLE IF NOT EXISTS odoo_webhook_event (
                        id BIGSERIAL PRIMARY KEY,
                        tipo VARCHAR(40) NOT NULL,
                        received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        remote_ip VARCHAR(128),
                        content_type VARCHAR(120),
                        payload TEXT,
                        matched_proyecto_id BIGINT,
                        action_taken VARCHAR(80),
                        note VARCHAR(500)
                    )
                    """);
            jdbc.execute(
                    "CREATE INDEX IF NOT EXISTS idx_odoo_webhook_received ON odoo_webhook_event (received_at DESC)");
            addColumnIfMissing("odoo_record_id", "BIGINT");
            addColumnIfMissing("odoo_model", "VARCHAR(80)");
            addColumnIfMissing("odoo_name", "VARCHAR(120)");
            addColumnIfMissing("odoo_display_name", "VARCHAR(255)");
            addColumnIfMissing("partner_id", "BIGINT");
            addColumnIfMissing("partner_name", "VARCHAR(255)");
            addColumnIfMissing("date_order", "VARCHAR(40)");
            addColumnIfMissing("amount_total", "VARCHAR(40)");
            addColumnIfMissing("odoo_state", "VARCHAR(40)");
        } catch (Exception ex) {
            log.warn("No se pudo crear odoo_webhook_event: {}", ex.getMessage());
        }
    }

    private void addColumnIfMissing(String column, String sqlType) {
        if (columnExists(column)) {
            return;
        }
        try {
            jdbc.execute("ALTER TABLE odoo_webhook_event ADD COLUMN " + column + " " + sqlType);
            log.info("odoo_webhook_event.{} creada", column);
        } catch (Exception ex) {
            log.warn("No se pudo crear odoo_webhook_event.{}: {}", column, ex.getMessage());
        }
    }

    private boolean columnExists(String column) {
        try {
            Integer n =
                    jdbc.queryForObject(
                            """
                            SELECT COUNT(*) FROM information_schema.columns
                            WHERE LOWER(table_name) = 'odoo_webhook_event' AND LOWER(column_name) = LOWER(?)
                            """,
                            Integer.class,
                            column);
            return n != null && n > 0;
        } catch (Exception ex) {
            return false;
        }
    }
}
