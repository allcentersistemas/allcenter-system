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
@Table(name = "rm_registro_salida_detalle")
@Getter
@Setter
@NoArgsConstructor
public class RmRegistroSalidaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registro_salida_id", nullable = false)
    private RmRegistroSalida registroSalida;

    @Column(length = 16)
    private String hora;

    @Column(nullable = false, length = 512)
    private String destino;

    @Column(length = 128)
    private String noRqmVale;

    @Column(length = 128)
    private String noGuia;

    @Column(nullable = false, length = 512)
    private String materialProducto;

    @Column(nullable = false, length = 64)
    private String cantidad;

    @Column(nullable = false, length = 64)
    private String unidad;

    @Column(nullable = false, length = 256)
    private String recibeFirma;

    @Column(nullable = false, length = 256)
    private String entregaRci;

    @Column(columnDefinition = "TEXT")
    private String photoFilenamesJson;
}
