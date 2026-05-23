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
    private String material;

    @Column(name = "cantidad", nullable = false, length = 64)
    private String cantidad;

    @Column(nullable = false, length = 64)
    private String unidad;

    @Column(columnDefinition = "TEXT")
    private String photoFilenamesJson;

    @Column(name = "categoria_codigo", length = 32)
    private String categoriaCodigo;

    @Column(columnDefinition = "TEXT")
    private String observaciones;
}
