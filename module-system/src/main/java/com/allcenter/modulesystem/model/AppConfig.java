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

    /** Importar medidas desde foto (planilla cliente) con visión IA. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean aiVisionEnabled = false;

    /** Proveedor: claude | openai */
    @Column(length = 32, columnDefinition = "varchar(32) default 'claude'")
    private String aiProvider = "claude";

    @Column(length = 80, columnDefinition = "varchar(80) default ''")
    private String aiModel = "";

    @Column(length = 512, columnDefinition = "varchar(512) default ''")
    private String aiApiKey = "";

    /**
     * Límite diario de importaciones por foto por cliente. {@code 0} = ilimitado. Default 20.
     */
    @Column(nullable = false, columnDefinition = "integer default 20")
    private int aiDailyLimitPerClient = 20;

    /** Notificaciones a clientes vía bot de Telegram. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean telegramEnabled = false;

    /** Token del bot (BotFather). No se expone en claro en la API. */
    @Column(length = 128, columnDefinition = "varchar(128) default ''")
    private String telegramBotToken = "";
}
