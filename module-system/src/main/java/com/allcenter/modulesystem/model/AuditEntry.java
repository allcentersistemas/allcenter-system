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
        name = "audit_entries",
        indexes = {
            @Index(name = "idx_audit_occurred_at", columnList = "occurred_at"),
            @Index(name = "idx_audit_entity", columnList = "entity_type,entity_id"),
            @Index(name = "idx_audit_client_ip_public", columnList = "client_ip_public")
        })
@Getter
@Setter
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditAction action;

    /** Tipo lógico: Employee, Role, AUTH, etc. */
    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(name = "actor_employee_id")
    private Long actorEmployeeId;

    @Column(name = "actor_client_user_id")
    private Long actorClientUserId;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    /** IP del socket TCP inmediato (suele ser balanceador / proxy frente al servidor). */
    @Column(name = "direct_remote_ip", length = 128)
    private String directRemoteIp;

    /**
     * Mejor aproximación a la IP pública del cliente (True-Client-IP, CF-Connecting-IP, primer hop de
     * X-Forwarded-For, X-Real-IP, o la directa si no hay cabeceras).
     */
    @Column(name = "client_ip_public", length = 128)
    private String clientIpPublic;

    /**
     * IP en la red local del dispositivo, solo si el cliente envía {@code X-Client-Local-Ip}
     * (aplicación móvil / agente).
     */
    @Column(name = "client_ip_local", length = 128)
    private String clientIpLocal;

    /**
     * Dirección MAC solo si el cliente de confianza envía {@code X-Client-Mac-Address}; no está
     * disponible en navegadores estándar.
     */
    @Column(name = "client_mac_address", length = 32)
    private String clientMacAddress;

    /** Nombre legible del dispositivo o composición desde Client Hints (Sec-CH-UA-*). */
    @Column(name = "device_name", length = 256)
    private String deviceName;

    /** Identificador de dispositivo enviado por el cliente ({@code X-Device-Id}). */
    @Column(name = "device_id", length = 128)
    private String deviceId;

    /** Valor crudo de X-Forwarded-For (cadena completa) para trazabilidad tras varios proxies. */
    @Column(name = "forwarded_for_chain", length = 2048)
    private String forwardedForChain;

    @Column(name = "user_agent", length = 2048)
    private String userAgent;

    @Column(columnDefinition = "TEXT")
    private String details;
}
