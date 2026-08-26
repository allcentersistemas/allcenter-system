package com.allcenter.modulesystem.model;

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

    /** PK de la obra en module-biesse ({@code ordenes.orderid}). */
    @Column(name = "biesse_order_id")
    private Long biesseOrderId;

    /** Nombre denormalizado de la obra Biesse para UI y matching de fulfillment. */
    @Column(name = "biesse_order_name")
    private String biesseOrderName;

    /** Código OP denormalizado desde la obra Biesse. */
    @Column(name = "op_codigo")
    private String opCodigo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyectoid", nullable = false)
    private ProyectoOptimizacion proyectoOptimizacionId;
}