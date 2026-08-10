package com.allcenter.modulesystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "proyecto_optimizacion")
@Getter
@Setter
public class ProyectoOptimizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "proyectoid")
    private Long id;

    @Column(name = "codigoproyecto")
    private Long codigoproyecto;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "cliente")
    private String cliente;

    @Column(name = "referencia")
    private String referencia;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "client_user_id")
    private Long clientUserId;

    @Column(name = "fechacreacion")
    private LocalDateTime fechacreacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 32)
    private ProyectoEstado estado = ProyectoEstado.ENVIADO;

    @Column(name = "vendedor_id")
    private Long vendedorId;

    @Column(name = "maquina_id")
    private Long maquinaId;

    @Column(name = "cotizacion_archivo", length = 512)
    private String cotizacionArchivo;

    @Column(name = "plano_archivo", length = 512)
    private String planoArchivo;

    @Column(name = "fecha_estado_enviado")
    private LocalDateTime fechaEstadoEnviado;

    @Column(name = "fecha_estado_en_atencion")
    private LocalDateTime fechaEstadoEnAtencion;

    @Column(name = "fecha_estado_cotizado")
    private LocalDateTime fechaEstadoCotizado;

    @Column(name = "fecha_estado_vendido")
    private LocalDateTime fechaEstadoVendido;

    @Column(name = "fecha_estado_cancelado")
    private LocalDateTime fechaEstadoCancelado;
}
