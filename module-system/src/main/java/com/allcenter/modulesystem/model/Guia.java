package com.allcenter.modulesystem.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "guia")
@Getter
@Setter
@NoArgsConstructor
public class Guia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Correlativo automático, p. ej. G-000001. */
    @Column(name = "numero_guia", nullable = false, unique = true, length = 32)
    private String numeroGuia;

    @Column(name = "estado", nullable = false, length = 32)
    private String estado;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

  /** Orden de compra asociada al despacho (opcional). */
    @Column(name = "orden_compra", length = 128)
    private String ordenCompra;

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

    @Column(name = "creado_por")
    private Long creadoPor;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @OneToMany(mappedBy = "guia", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Guiadetalle> detalles = new ArrayList<>();
}
