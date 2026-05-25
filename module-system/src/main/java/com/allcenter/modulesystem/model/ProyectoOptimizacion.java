package com.allcenter.modulesystem.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

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

    @Column(name = "fechacreacion")
    private LocalDateTime fechacreacion;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "client_user_id")
    private Long clientUserId;

}
