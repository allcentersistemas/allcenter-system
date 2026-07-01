package com.allcenter.modulesystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "backup_run")
@Getter
@Setter
public class BackupRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 20)
    private String triggerType;

    @Column(length = 2000)
    private String message;

    @Column(length = 1000)
    private String fileNames;

    private Long totalBytes;

    @Column(nullable = false)
    private boolean emailed;

    @Column(nullable = false)
    private int progressPercent = 0;

    @Column(length = 120)
    private String progressStage = "";
}
