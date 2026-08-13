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

    @Column(name = "material", length = 512)
    private String material;

    /** JSON de cantos, ranuras, etc. Puede superar 255 fácilmente. */
    @Column(name = "parametros", columnDefinition = "TEXT")
    private String parametros;

    @Column(name = "cantidad")
    private Integer cantidad;

    @Column(name = "largo")
    private Integer largo;

    @Column(name = "ancho")
    private Integer ancho;

    @Column(name = "veta", length = 64)
    private String veta;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "descripcion1", length = 64)
    private String descripcion1;




    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordenid", nullable = false)
    private Orden ordenId;

}
