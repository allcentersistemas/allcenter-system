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

    @Column(name = "odoo_record_id")
    private Long odooRecordId;

    @Column(name = "odoo_model", length = 80)
    private String odooModel;

    @Column(name = "odoo_name", length = 120)
    private String odooName;

    @Column(name = "odoo_display_name", length = 255)
    private String odooDisplayName;

    @Column(name = "partner_id")
    private Long partnerId;

    @Column(name = "partner_name", length = 255)
    private String partnerName;

    @Column(name = "date_order", length = 40)
    private String dateOrder;

    @Column(name = "amount_total", length = 40)
    private String amountTotal;

    @Column(name = "odoo_state", length = 40)
    private String odooState;
}
