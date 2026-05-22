package com.allcenter.modulesystem.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Columnas del registro cliente (persona natural / jurídica). Idempotente. */
@Component
public class ClientUserSchemaAligner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClientUserSchemaAligner.class);

    private final JdbcTemplate jdbc;

    public ClientUserSchemaAligner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("client_users")) {
            return;
        }
        addColumnIfMissing("username", "VARCHAR(64)");
        addColumnIfMissing("nombre", "VARCHAR(180)");
        addColumnIfMissing("tipo_documento", "VARCHAR(16)");
        addColumnIfMissing("ciudad", "VARCHAR(120)");
        addColumnIfMissing("razon_social", "VARCHAR(180)");
        addColumnIfMissing("ruc", "VARCHAR(20)");
        addColumnIfMissing("juridica", "BOOLEAN NOT NULL DEFAULT false");
        backfillUsernameFromEmail();
    }

    private void backfillUsernameFromEmail() {
        try {
            jdbc.update(
                    """
                    UPDATE client_users
                    SET username = SPLIT_PART(email, '@', 1)
                    WHERE username IS NULL OR TRIM(username) = ''
                    """);
        } catch (Exception ex) {
            log.debug("Backfill username omitido: {}", ex.getMessage());
        }
    }

    private void addColumnIfMissing(String column, String sqlType) {
        if (columnExists("client_users", column)) {
            return;
        }
        try {
            jdbc.execute("ALTER TABLE client_users ADD COLUMN " + column + " " + sqlType);
            log.info("client_users.{} creada", column);
        } catch (Exception ex) {
            log.warn("No se pudo crear client_users.{}: {}", column, ex.getMessage());
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
