package com.allcenter.modulesystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rm_registro_entrada_detalle")
@Getter
@Setter
@NoArgsConstructor
public class RmRegistroEntradaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registro_entrada_id", nullable = false)
    private RmRegistroEntrada registroEntrada;

    @Column(nullable = false, length = 512)
    private String proveedor;

    @Column(length = 128)
    private String ocNumero;

    @Column(length = 128)
    private String guiaNumero;

    @Column(nullable = false, length = 512)
    private String material;

    @Column(length = 256)
    private String colorModelo;

    @Column(length = 64)
    private String cantidadRecibida;

    @Column(length = 64)
    private String unidad;

    @Column(columnDefinition = "TEXT")
    private String photoFilenamesJson;
}
