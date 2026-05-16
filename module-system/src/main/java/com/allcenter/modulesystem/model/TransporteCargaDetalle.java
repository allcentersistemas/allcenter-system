package com.allcenter.modulesystem.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "transportecargadetalle")
@Getter
@Setter


public class TransporteCargaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transportecargadetalleid")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transportecargaid", nullable = false)
    private TransporteCarga transporteCarga;

    @Column(name = "paleenvioid", nullable = false)
    private Long paleEnvioId;

    @Column(name = "palecodigo")
    private String paleCodigo;

    @Column(name = "cantidad")
    private Integer cantidad;

    @Column(name = "observacion")
    private String observacion;

    @Column(name = "fecharegistro")
    private LocalDateTime fechaRegistro;
}
