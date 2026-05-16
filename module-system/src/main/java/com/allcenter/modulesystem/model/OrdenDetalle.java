package com.allcenter.modulesystem.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ordendetalle")
@Getter
@Setter

public class OrdenDetalle {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "orderdetalleid")
    private Long id;

    @Column(name = "material")
    private String material;

    @Column(name = "parametros")
    private String parametros;

    @Column(name = "cantidad")
    private Integer cantidad;

    @Column(name = "largo")
    private Integer largo;

    @Column(name = "ancho")
    private Integer ancho;

    @Column(name = "veta")
    private String veta;

    @Column(name =  "descripcion")
    private String descripcion;

    @Column(name = "descripcion1")
    private String descripcion1;




    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordenid", nullable = false)
    private Orden ordenId;

}
