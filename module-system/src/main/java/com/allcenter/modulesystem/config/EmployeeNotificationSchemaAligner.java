package com.allcenter.modulesystem.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Tabla de notificaciones in-app para empleados (badge / SSE). Idempotente. */
@Component
public class EmployeeNotificationSchemaAligner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmployeeNotificationSchemaAligner.class);

    private final JdbcTemplate jdbc;

    public EmployeeNotificationSchemaAligner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureTable();
    }

    private void ensureTable() {
        if (tableExists("employee_notifications")) {
            return;
        }
        try {
            jdbc.execute(
                    """
                    CREATE TABLE employee_notifications (
                      id BIGSERIAL PRIMARY KEY,
                      employee_id BIGINT NOT NULL,
                      notification_type VARCHAR(64) NOT NULL,
                      title VARCHAR(255) NOT NULL,
                      body TEXT,
                      payload_json TEXT,
                      read_at TIMESTAMP,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            jdbc.execute(
                    """
                    CREATE INDEX idx_employee_notifications_employee_unread
                    ON employee_notifications (employee_id, read_at, created_at DESC)
                    """);
            log.info("Tabla employee_notifications creada");
        } catch (Exception ex) {
            log.warn("No se pudo crear employee_notifications: {}", ex.getMessage());
        }
    }

    private boolean tableExists(String table) {
        try {
            Integer n =
                    jdbc.queryForObject(
                            """
                            SELECT COUNT(*) FROM information_schema.tables
                            WHERE LOWER(table_name) = LOWER(?)
                            """,
                            Integer.class,
                            table);
            return n != null && n > 0;
        } catch (Exception ex) {
            return false;
        }
    }
}
