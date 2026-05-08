package com.allcenter.moduletransport.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "transportecarga")
@Getter
@Setter


public class TransporteCarga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transportecargaid")
    private Long id;

    @Column(name = "employee")
    private String choferNombre;

    @Column(name = "choferdocumento")
    private String choferDocumento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transporteid", nullable = false)
    private Transporte transporte;

    @Column(name = "estado")
    private String estado;

    @Column(name = "notas")
    private String notas;

    @Column(name = "creadopor")
    private Long creadoPor;

    @Column(name = "fechasalida")
    private LocalDateTime fechaSalida;

    @Column(name = "fechaentrega")
    private LocalDateTime fechaEntrega;

    @Column(name = "fechacreacion")
    private LocalDateTime fechaCreacion;
}
