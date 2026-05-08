package com.allcenter.moduleorder.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "orden")
@Getter
@Setter
public class Orden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ordenid")
    private Long id;

    @Column(name = "ordername")
    private String orderName;

    @Column(name = "ordercode")
    private String orderCode;

    @Column(name = "descripcion")
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyectoid", nullable = false)
    private Proyecto proyectoId;
}