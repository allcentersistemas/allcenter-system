package com.allcenter.modulesystem.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Columna de propiedad del proyecto (portal cliente). Idempotente. */
@Component
public class ProyectoOptimizacionSchemaAligner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProyectoOptimizacionSchemaAligner.class);

    private final JdbcTemplate jdbc;

    public ProyectoOptimizacionSchemaAligner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("proyecto_optimizacion")) {
            return;
        }
        addColumnIfMissing("proyecto_optimizacion", "client_user_id", "BIGINT");
        addColumnIfMissing("proyecto_optimizacion", "fechacreacion", "TIMESTAMP");
        addColumnIfMissing("proyecto_optimizacion", "estado", "VARCHAR(32)");
        addColumnIfMissing("proyecto_optimizacion", "vendedor_id", "BIGINT");
        addColumnIfMissing("proyecto_optimizacion", "maquina_id", "BIGINT");
        addColumnIfMissing("proyecto_optimizacion", "cotizacion_archivo", "VARCHAR(512)");
        addColumnIfMissing("proyecto_optimizacion", "fecha_estado_enviado", "TIMESTAMP");
        addColumnIfMissing("proyecto_optimizacion", "fecha_estado_en_atencion", "TIMESTAMP");
        addColumnIfMissing("proyecto_optimizacion", "fecha_estado_cotizado", "TIMESTAMP");
        addColumnIfMissing("proyecto_optimizacion", "fecha_estado_vendido", "TIMESTAMP");
        addColumnIfMissing("proyecto_optimizacion", "fecha_estado_cancelado", "TIMESTAMP");
        jdbc.update(
                """
                UPDATE proyecto_optimizacion
                SET estado = 'ENVIADO'
                WHERE estado IS NULL OR TRIM(estado) = ''
                """);
        jdbc.update(
                """
                UPDATE proyecto_optimizacion
                SET fechacreacion = CURRENT_TIMESTAMP
                WHERE fechacreacion IS NULL
                """);
        jdbc.update(
                """
                UPDATE proyecto_optimizacion
                SET fecha_estado_enviado = fechacreacion
                WHERE fecha_estado_enviado IS NULL
                  AND (estado IS NULL OR estado IN ('ENVIADO', 'EN_ATENCION', 'COTIZADO', 'VENDIDO', 'CANCELADO'))
                """);
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
