package com.allcenter.modulesystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "transport_audit_entry",
        indexes = {
            @Index(name = "idx_transport_audit_occurred", columnList = "occurred_at"),
            @Index(name = "idx_transport_audit_entity", columnList = "entity_type,entity_id"),
            @Index(name = "idx_transport_audit_correlation", columnList = "correlation_id")
        })
@Getter
@Setter
public class TransportAuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transportauditentryid")
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransportAuditAction action;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    /**
     * Agrupa eventos de una misma guía (ID de {@code Guia}) para trazabilidad de punta a
     * punta; en vehículos suele coincidir con {@code entityId}.
     */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "actor_employee_id")
    private Long actorEmployeeId;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Column(name = "client_ip_public", length = 128)
    private String clientIpPublic;

    @Column(name = "forwarded_for_chain", length = 2048)
    private String forwardedForChain;

    @Column(name = "user_agent", length = 2048)
    private String userAgent;

    @Column(columnDefinition = "TEXT")
    private String details;
}
