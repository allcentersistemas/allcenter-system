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
        ensureRmSalidaHeaderColumns();
        ensureRmRegistroNumeroAndVehiculoLink();
        ensureRmEntradaDestinoColumn();
        ensureRmEntradaGuiaColumn();
        ensureRmEntradaNumeroGuiaColumn();
        ensureRmActaTransporteColumns();
        ensureRmSalidaGuiaColumn();
        ensureRmSalidaDetalleOptionalColumns();
        ensureGuiaOrdenCompraColumn();
        syncPaleEnGuiaFromDetalles();
        relaxLegacyColumns();
        dropLegacyColumns();
    }

    private void ensureGuiaOrdenCompraColumn() {
        addColumnIfMissing("guia", "orden_compra", "VARCHAR(128)");
    }

    /** Marca en_guia=true en pales que ya figuran en guiadetalle (migración). */
    private void syncPaleEnGuiaFromDetalles() {
        if (!tableExists("pale") || !tableExists("guiadetalle") || !columnExists("pale", "en_guia")) {
            return;
        }
        if (!columnExists("guiadetalle", "pale_id")) {
            return;
        }
        try {
            jdbc.execute(
                    """
                    UPDATE pale SET en_guia = true
                    WHERE paleeid IN (
                        SELECT DISTINCT pale_id FROM guiadetalle WHERE pale_id IS NOT NULL
                    )
                    """);
        } catch (Exception ex) {
            log.warn("No se pudo sincronizar pale.en_guia desde guiadetalle: {}", ex.getMessage());
        }
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

    private void ensureRmSalidaHeaderColumns() {
        if (!tableExists("rm_registro_salida")) {
            return;
        }
        addColumnIfMissing("rm_registro_salida", "destino", "VARCHAR(512)");
        addColumnIfMissing("rm_registro_salida", "numero_guia", "VARCHAR(128)");
        addColumnIfMissing("rm_registro_salida", "orden_compra", "VARCHAR(128)");
    }

    private void ensureRmRegistroNumeroAndVehiculoLink() {
        addColumnIfMissing("rm_registro_vehiculo", "numeroregistro", "INTEGER");
        addColumnIfMissing("rm_registro_vehiculo", "tiporegistro", "VARCHAR(32)");
        addColumnIfMissing("rm_registro_vehiculo", "guia_numero", "VARCHAR(128)");
        addColumnIfMissing("rm_registro_vehiculo", "oc_numero", "VARCHAR(128)");
        addColumnIfMissing("rm_registro_entrada", "numeroregistro", "INTEGER");
        addColumnIfMissing("rm_registro_salida", "numeroregistro", "INTEGER");
        addColumnIfMissing("rm_registro_salida", "registro_vehiculo_id", "BIGINT");
    }

    private void ensureRmEntradaGuiaColumn() {
        if (!tableExists("rm_registro_entrada")) {
            return;
        }
        addColumnIfMissing("rm_registro_entrada", "guia_inventario_id", "BIGINT");
    }

    /** Alinea columna legacy guia_numero → numero_guia (modelo RmRegistroEntrada). */
    private void ensureRmEntradaNumeroGuiaColumn() {
        if (!tableExists("rm_registro_entrada")) {
            return;
        }
        if (columnExists("rm_registro_entrada", "numero_guia")) {
            return;
        }
        if (columnExists("rm_registro_entrada", "guia_numero")) {
            try {
                jdbc.execute("ALTER TABLE rm_registro_entrada RENAME COLUMN guia_numero TO numero_guia");
                log.info("rm_registro_entrada.guia_numero renombrada a numero_guia");
            } catch (Exception ex) {
                log.warn("No se pudo renombrar rm_registro_entrada.guia_numero: {}", ex.getMessage());
            }
            return;
        }
        addColumnIfMissing("rm_registro_entrada", "numero_guia", "VARCHAR(128)");
    }

    private void ensureRmActaTransporteColumns() {
        if (!tableExists("rm_acta_conformidad")) {
            return;
        }
        addColumnIfMissing("rm_acta_conformidad", "transporte_id", "BIGINT");
        addColumnIfMissing("rm_acta_conformidad", "chofer_nombre", "VARCHAR(256)");
    }

    private void ensureRmEntradaDestinoColumn() {
        if (!tableExists("rm_registro_entrada")) {
            return;
        }
        addColumnIfMissing("rm_registro_entrada", "destino", "VARCHAR(512)");
        if (tableExists("rm_registro_entrada_detalle") && !columnExists("rm_registro_entrada_detalle", "cantidad")) {
            if (columnExists("rm_registro_entrada_detalle", "cantidad_recibida")) {
                try {
                    jdbc.execute(
                            "ALTER TABLE rm_registro_entrada_detalle RENAME COLUMN cantidad_recibida TO cantidad");
                    log.info("rm_registro_entrada_detalle.cantidad_recibida renombrada a cantidad");
                } catch (Exception ex) {
                    addColumnIfMissing("rm_registro_entrada_detalle", "cantidad", "VARCHAR(64)");
                }
            } else {
                addColumnIfMissing("rm_registro_entrada_detalle", "cantidad", "VARCHAR(64)");
            }
        }
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

    /** recibe_firma y entrega_rci ya no son obligatorios en la app Android. */
    private void ensureRmSalidaDetalleOptionalColumns() {
        if (!tableExists("rm_registro_salida_detalle")) {
            return;
        }
        for (String col :
                new String[] {
                    "recibe_firma",
                    "entrega_rci",
                    "destino",
                    "no_guia",
                    "no_rqm_vale",
                    "proveedor",
                    "color_modelo",
                    "cantidad_recibida"
                }) {
            if (!columnExists("rm_registro_salida_detalle", col)) {
                continue;
            }
            try {
                jdbc.execute("ALTER TABLE rm_registro_salida_detalle ALTER COLUMN " + col + " DROP NOT NULL");
                log.info("rm_registro_salida_detalle.{} admite NULL", col);
            } catch (Exception ex) {
                log.warn("No se pudo relajar NOT NULL en rm_registro_salida_detalle.{}: {}", col, ex.getMessage());
            }
        }
        if (tableExists("rm_registro_entrada_detalle")) {
            for (String col : new String[] {"proveedor", "color_modelo", "cantidad_recibida"}) {
                if (!columnExists("rm_registro_entrada_detalle", col)) {
                    continue;
                }
                try {
                    jdbc.execute("ALTER TABLE rm_registro_entrada_detalle ALTER COLUMN " + col + " DROP NOT NULL");
                    log.info("rm_registro_entrada_detalle.{} admite NULL", col);
                } catch (Exception ex) {
                    log.warn("No se pudo relajar NOT NULL en rm_registro_entrada_detalle.{}: {}", col, ex.getMessage());
                }
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
