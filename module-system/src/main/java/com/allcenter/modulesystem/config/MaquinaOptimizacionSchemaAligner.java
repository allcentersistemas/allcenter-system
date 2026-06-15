package com.allcenter.modulesystem.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MaquinaOptimizacionSchemaAligner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MaquinaOptimizacionSchemaAligner.class);

    private final JdbcTemplate jdbc;

    public MaquinaOptimizacionSchemaAligner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureTable();
        seedDefaults();
    }

    private void ensureTable() {
        if (tableExists("maquina_optimizacion")) {
            return;
        }
        try {
            jdbc.execute(
                    """
                    CREATE TABLE maquina_optimizacion (
                        maquinaid BIGSERIAL PRIMARY KEY,
                        codigo VARCHAR(128) NOT NULL UNIQUE,
                        nombre VARCHAR(256) NOT NULL,
                        activo BOOLEAN NOT NULL DEFAULT TRUE,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            log.info("tabla maquina_optimizacion creada");
        } catch (Exception ex) {
            log.warn("No se pudo crear tabla maquina_optimizacion: {}", ex.getMessage());
        }
    }

    private void seedDefaults() {
        if (!tableExists("maquina_optimizacion")) {
            return;
        }
        try {
            Integer count =
                    jdbc.queryForObject("SELECT COUNT(*) FROM maquina_optimizacion", Integer.class);
            if (count != null && count > 0) {
                return;
            }
            jdbc.update(
                    """
                    INSERT INTO maquina_optimizacion (codigo, nombre, activo)
                    VALUES ('DEF - SEKTOR470', 'Sector 470 (default)', TRUE)
                    """);
            log.info("máquina por defecto insertada");
        } catch (Exception ex) {
            log.warn("No se pudo insertar máquina por defecto: {}", ex.getMessage());
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
