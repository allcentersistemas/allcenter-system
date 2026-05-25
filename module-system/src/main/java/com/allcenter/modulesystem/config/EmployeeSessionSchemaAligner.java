package com.allcenter.modulesystem.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Columnas de sesión única en empleados y metadatos en refresh_tokens. */
@Component
public class EmployeeSessionSchemaAligner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmployeeSessionSchemaAligner.class);

    private final JdbcTemplate jdbc;

    public EmployeeSessionSchemaAligner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tableExists("employees")) {
            addColumnIfMissing("employees", "session_connected", "BOOLEAN NOT NULL DEFAULT false");
            addColumnIfMissing("employees", "session_client_ip", "VARCHAR(64)");
            addColumnIfMissing("employees", "session_client_hostname", "VARCHAR(255)");
            addColumnIfMissing("employees", "session_last_seen_at", "TIMESTAMP");
        }
        if (tableExists("refresh_tokens")) {
            addColumnIfMissing("refresh_tokens", "client_ip", "VARCHAR(64)");
            addColumnIfMissing("refresh_tokens", "client_hostname", "VARCHAR(255)");
            addColumnIfMissing("refresh_tokens", "last_activity_at", "TIMESTAMP");
        }
    }

    private void addColumnIfMissing(String table, String column, String sqlType) {
        if (columnExists(table, column)) {
            return;
        }
        try {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + sqlType);
            log.info("{}.{} creada", table, column);
        } catch (Exception ex) {
            log.warn("No se pudo crear {}.{}: {}", table, column, ex.getMessage());
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

    private boolean columnExists(String table, String column) {
        try {
            Integer n =
                    jdbc.queryForObject(
                            """
                            SELECT COUNT(*) FROM information_schema.columns
                            WHERE LOWER(table_name) = LOWER(?) AND LOWER(column_name) = LOWER(?)
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
