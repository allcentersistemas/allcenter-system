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
@Table(name = "odoo_webhook_event")
@Getter
@Setter
public class OdooWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "remote_ip", length = 128)
    private String remoteIp;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "matched_proyecto_id")
    private Long matchedProyectoId;

    @Column(name = "action_taken", length = 80)
    private String actionTaken;

    @Column(length = 500)
    private String note;
}
