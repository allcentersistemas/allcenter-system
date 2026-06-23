package com.allcenter.modulesystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "backup_config")
@Getter
@Setter
public class BackupConfig {

    @Id
    private Long id = 1L;

    @Column(nullable = false)
    private boolean enabled = false;

    /** Horas entre backups automáticos (mín. 1). Por defecto: diario (24 h). */
    @Column(nullable = false)
    private int intervalHours = 24;

    /** Hora del día (0–23) cuando intervalHours >= 24. Por defecto: 3:00. */
    @Column(nullable = false)
    private int scheduledHour = 3;

    @Column(nullable = false)
    private boolean saveToFolder = true;

    @Column(nullable = false)
    private boolean sendByEmail = false;

    @Column(length = 2000)
    private String emailRecipients = "";

    @Column(nullable = false)
    private boolean includeBiesseDb = true;

    @Column(nullable = false)
    private int retentionCount = 7;

    private Instant lastSuccessfulRunAt;
}
