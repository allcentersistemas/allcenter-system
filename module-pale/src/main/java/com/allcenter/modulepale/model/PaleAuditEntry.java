package com.allcenter.modulepale.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "pale_audit_entry")
@Getter
@Setter
public class PaleAuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(nullable = false, length = 80)
    private String entityType;

    @Column(length = 80)
    private String entityId;

    @Column
    private Long paleId;

    @Column(length = 80)
    private String paleCodigo;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "actor_employee_id")
    private Long actorEmployeeId;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Column(name = "source_ip", length = 128)
    private String sourceIp;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
}
