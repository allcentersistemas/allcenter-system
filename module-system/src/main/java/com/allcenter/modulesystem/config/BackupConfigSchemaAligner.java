package com.allcenter.modulesystem.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Columnas de backup_config. Debe ejecutarse antes de BackupService (@DependsOn). */
@Component
public class BackupConfigSchemaAligner {

    private static final Logger log = LoggerFactory.getLogger(BackupConfigSchemaAligner.class);

    private final JdbcTemplate jdbc;

    public BackupConfigSchemaAligner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void align() {
        if (!tableExists("backup_config")) {
            return;
        }
        addColumnIfMissing("backup_config", "include_media_files", "BOOLEAN NOT NULL DEFAULT true");
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
