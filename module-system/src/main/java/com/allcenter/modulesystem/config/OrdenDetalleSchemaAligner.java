package com.allcenter.modulesystem.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Amplía columnas de {@code ordendetalle} que se quedaban cortas en VARCHAR(255)
 * (sobre todo {@code parametros}, que guarda JSON de cantos/ranuras).
 */
@Component
public class OrdenDetalleSchemaAligner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrdenDetalleSchemaAligner.class);

    private final JdbcTemplate jdbc;

    public OrdenDetalleSchemaAligner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("ordendetalle")) {
            return;
        }
        widenToText("ordendetalle", "parametros");
        widenToText("ordendetalle", "descripcion");
        widenVarchar("ordendetalle", "material", 512);
        widenVarchar("ordendetalle", "veta", 64);
        widenVarchar("ordendetalle", "descripcion1", 64);
    }

    private void widenToText(String table, String column) {
        if (!columnExists(table, column)) {
            return;
        }
        String dataType = columnDataType(table, column);
        if (dataType != null && (dataType.equalsIgnoreCase("text") || dataType.equalsIgnoreCase("clob"))) {
            return;
        }
        try {
            jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " TYPE TEXT");
            log.info("{}.{} ampliada a TEXT", table, column);
        } catch (Exception ex) {
            log.warn("No se pudo ampliar {}.{} a TEXT: {}", table, column, ex.getMessage());
        }
    }

    private void widenVarchar(String table, String column, int length) {
        if (!columnExists(table, column)) {
            return;
        }
        Integer current = varcharLength(table, column);
        if (current != null && current >= length) {
            return;
        }
        String dataType = columnDataType(table, column);
        if (dataType != null && dataType.equalsIgnoreCase("text")) {
            return;
        }
        try {
            jdbc.execute(
                    "ALTER TABLE " + table + " ALTER COLUMN " + column + " TYPE VARCHAR(" + length + ")");
            log.info("{}.{} ampliada a VARCHAR({})", table, column, length);
        } catch (Exception ex) {
            log.warn("No se pudo ampliar {}.{}: {}", table, column, ex.getMessage());
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

    private String columnDataType(String table, String column) {
        try {
            return jdbc.queryForObject(
                    """
                    SELECT data_type FROM information_schema.columns
                    WHERE LOWER(table_name) = LOWER(?) AND LOWER(column_name) = LOWER(?)
                    LIMIT 1
                    """,
                    String.class,
                    table,
                    column);
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer varcharLength(String table, String column) {
        try {
            return jdbc.queryForObject(
                    """
                    SELECT character_maximum_length FROM information_schema.columns
                    WHERE LOWER(table_name) = LOWER(?) AND LOWER(column_name) = LOWER(?)
                    LIMIT 1
                    """,
                    Integer.class,
                    table,
                    column);
        } catch (Exception ex) {
            return null;
        }
    }
}
