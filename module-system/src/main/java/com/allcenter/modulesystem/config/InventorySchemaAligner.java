package com.allcenter.modulesystem.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Columnas de inventario por sucursal, categorías y observaciones (idempotente). */
@Component
public class InventorySchemaAligner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InventorySchemaAligner.class);

    private final JdbcTemplate jdbc;

    public InventorySchemaAligner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureInvMovementColumns();
        ensurePaleSucursal();
        ensureRmObservacionesAndCategoria();
    }

    private void ensureInvMovementColumns() {
        if (!tableExists("inv_stock_movement")) {
            return;
        }
        addColumnIfMissing("inv_stock_movement", "sucursal_id", "BIGINT");
        addColumnIfMissing("inv_stock_movement", "categoria_codigo", "VARCHAR(32)");
        addColumnIfMissing("inv_stock_movement", "observaciones", "TEXT");
    }

    private void ensurePaleSucursal() {
        if (!tableExists("pale")) {
            return;
        }
        addColumnIfMissing("pale", "sucursal_id", "BIGINT");
    }

    private void ensureRmObservacionesAndCategoria() {
        addColumnIfMissing("rm_registro_entrada", "observaciones", "TEXT");
        addColumnIfMissing("rm_registro_salida", "observaciones", "TEXT");
        addColumnIfMissing("rm_registro_entrada_detalle", "categoria_codigo", "VARCHAR(32)");
        addColumnIfMissing("rm_registro_entrada_detalle", "observaciones", "TEXT");
        addColumnIfMissing("rm_registro_salida_detalle", "categoria_codigo", "VARCHAR(32)");
        addColumnIfMissing("rm_registro_salida_detalle", "observaciones", "TEXT");
    }

    private void addColumnIfMissing(String table, String column, String sqlType) {
        if (!tableExists(table) || columnExists(table, column)) {
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
