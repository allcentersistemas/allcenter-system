package com.allcenter.modulepale.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import com.allcenter.modulelocation.model.Sucursal;
import com.allcenter.modulelocation.model.Ubicacion;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "pale")
@Getter
@Setter
public class Pale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "paleeid")
    private Long id;

    @Column(name = "codigo", nullable = false, unique = true, length = 80)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_origen_id")
    private Sucursal sucursalOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubicacion_origen_id")
    private Ubicacion ubicacionOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_destino_id")
    private Sucursal sucursalDestino;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubicacion_destino_id")
    private Ubicacion ubicacionDestino;

    @Column(name = "estado", nullable = false, length = 40)
    private String estado;

    @Column(name = "cantidad_piezas", nullable = false)
    private Integer cantidadPiezas;

    @Column(name = "cantidad_ordenes", nullable = false)
    private Integer cantidadOrdenes;

    @Column(name = "ordenes_resumen", columnDefinition = "TEXT")
    private String ordenesResumen;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @Column(name = "creado_por")
    private Long creadoPor;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Column(name = "estado_envio")
    private String estadoEnvio;
}