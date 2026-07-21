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
@Table(name = "planilla_ai_usage")
@Getter
@Setter
public class PlanillaAiUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_user_id", nullable = false)
    private Long clientUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(length = 32)
    private String provider = "";

    @Column(length = 80)
    private String model = "";

    @Column(nullable = false)
    private boolean success;

    @Column(name = "filas_count", nullable = false)
    private int filasCount;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "reject_reason", length = 1000)
    private String rejectReason;

    @Column(name = "original_filename", length = 260)
    private String originalFilename;

    @Column
    private Long bytes;
}
