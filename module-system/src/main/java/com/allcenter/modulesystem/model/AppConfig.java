package com.allcenter.modulesystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "app_config")
@Getter
@Setter
public class AppConfig {

    @Id
    private Long id = 1L;

    /** Si false, no se registran movimientos automáticos de kardex (palés, RM, etc.). */
    @Column(nullable = false)
    private boolean kardexEnabled = true;

    @Column(nullable = false)
    private boolean mailEnabled = false;

    @Column(length = 320)
    private String mailFrom = "";

    @Column(length = 128)
    private String mailFromName = "";

    @Column(length = 256)
    private String smtpHost = "";

    @Column(nullable = false)
    private int smtpPort = 587;

    @Column(length = 320)
    private String smtpUsername = "";

    @Column(length = 512)
    private String smtpPassword = "";

    @Column(nullable = false)
    private boolean smtpAuth = false;

    @Column(nullable = false)
    private boolean smtpStarttls = true;
}
