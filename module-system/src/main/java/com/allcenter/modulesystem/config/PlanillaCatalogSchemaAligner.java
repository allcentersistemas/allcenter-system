package com.allcenter.modulesystem.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Tablas catálogo tablero/canto (planilla cliente). Idempotente. */
@Component
public class PlanillaCatalogSchemaAligner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlanillaCatalogSchemaAligner.class);

    private final JdbcTemplate jdbc;

    public PlanillaCatalogSchemaAligner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureTableroTable();
        ensureCantoTable();
    }

    private void ensureTableroTable() {
        if (tableExists("tablero")) {
            return;
        }
        try {
            jdbc.execute(
                    """
                    CREATE TABLE tablero (
                        tableroid BIGSERIAL PRIMARY KEY,
                        codigo VARCHAR(64) NOT NULL UNIQUE,
                        nombre VARCHAR(512) NOT NULL,
                        espesor_mm INTEGER,
                        unidad VARCHAR(32) NOT NULL DEFAULT 'PLN',
                        active BOOLEAN NOT NULL DEFAULT TRUE,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            log.info("tabla tablero creada");
        } catch (Exception ex) {
            log.warn("No se pudo crear tabla tablero: {}", ex.getMessage());
        }
    }

    private void ensureCantoTable() {
        if (tableExists("canto")) {
            return;
        }
        try {
            jdbc.execute(
                    """
                    CREATE TABLE canto (
                        cantoid BIGSERIAL PRIMARY KEY,
                        codigo VARCHAR(64) NOT NULL UNIQUE,
                        nombre VARCHAR(512) NOT NULL,
                        active BOOLEAN NOT NULL DEFAULT TRUE,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            log.info("tabla canto creada");
        } catch (Exception ex) {
            log.warn("No se pudo crear tabla canto: {}", ex.getMessage());
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
