package com.allcenter.modulesystem.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Alinea la tabla {@code guia} al modelo de inventario (sin chofer ni transporte obligatorios).
 * Idempotente: seguro ejecutar en cada arranque.
 */
@Component
public class GuiaSchemaAligner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GuiaSchemaAligner.class);

    private final JdbcTemplate jdbc;

    public GuiaSchemaAligner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("guia")) {
            return;
        }
        ensureOrigenColumns();
        ensureRmSalidaGuiaColumn();
        relaxLegacyColumns();
        dropLegacyColumns();
    }

    private void ensureRmSalidaGuiaColumn() {
        if (!tableExists("rm_registro_salida")) {
            return;
        }
        if (!columnExists("rm_registro_salida", "guia_inventario_id")) {
            try {
                jdbc.execute("ALTER TABLE rm_registro_salida ADD COLUMN guia_inventario_id BIGINT");
                log.info("Columna rm_registro_salida.guia_inventario_id creada");
            } catch (Exception ex) {
                log.warn("No se pudo crear rm_registro_salida.guia_inventario_id: {}", ex.getMessage());
            }
        }
    }

    private void ensureOrigenColumns() {
        if (!columnExists("guia", "sucursal_origen_id")) {
            try {
                jdbc.execute("ALTER TABLE guia ADD COLUMN sucursal_origen_id BIGINT");
                log.info("Columna guia.sucursal_origen_id creada");
            } catch (Exception ex) {
                log.warn("No se pudo crear guia.sucursal_origen_id: {}", ex.getMessage());
            }
        }
        if (!columnExists("guia", "ubicacion_origen_id")) {
            try {
                jdbc.execute("ALTER TABLE guia ADD COLUMN ubicacion_origen_id BIGINT");
                log.info("Columna guia.ubicacion_origen_id creada");
            } catch (Exception ex) {
                log.warn("No se pudo crear guia.ubicacion_origen_id: {}", ex.getMessage());
            }
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
            log.debug("No se pudo comprobar tabla {}: {}", table, ex.getMessage());
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

    private void relaxLegacyColumns() {
        for (String col : new String[] {"chofer_nombre", "chofer_documento", "transporte_id"}) {
            if (!columnExists("guia", col)) {
                continue;
            }
            try {
                jdbc.execute("ALTER TABLE guia ALTER COLUMN " + col + " DROP NOT NULL");
                log.info("guia.{} ya admite NULL", col);
            } catch (Exception ex) {
                log.warn("No se pudo relajar NOT NULL en guia.{}: {}", col, ex.getMessage());
            }
        }
    }

    private void dropLegacyColumns() {
        for (String col :
                new String[] {
                    "chofer_nombre",
                    "chofer_documento",
                    "transporte_id",
                    "fecha_salida",
                    "fecha_entrega"
                }) {
            if (!columnExists("guia", col)) {
                continue;
            }
            try {
                jdbc.execute("ALTER TABLE guia DROP COLUMN " + col);
                log.info("Columna legacy guia.{} eliminada", col);
            } catch (Exception ex) {
                log.warn("No se pudo eliminar guia.{}: {}", col, ex.getMessage());
            }
        }
    }
}
