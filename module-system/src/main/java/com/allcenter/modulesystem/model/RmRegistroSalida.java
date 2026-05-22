package com.allcenter.modulesystem.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Formula;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rm_registro_salida")
@Getter
@Setter
@NoArgsConstructor
public class RmRegistroSalida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    /** Hora de cabecera (formato HH:MM). */
    @Column(nullable = false, length = 16)
    private String horaCabecera;

    /** Vehículo de flota (module-system), opcional. */
    @Column(name = "transporte_id")
    private Long transporteId;

    /** Guía de despacho (inventario) asociada a esta salida RM. */
    @Column(name = "guia_inventario_id")
    private Long guiaInventarioId;

    @Column(columnDefinition = "TEXT")
    private String cabeceraPhotoFilenamesJson;

    @Column(name = "chofer_salida_empleado_id")
    private Long choferSalidaEmpleadoId;

    @Column(length = 256)
    private String choferSalidaNombre;

    @Column(length = 32)
    private String recepcionEstado;

    private Instant validadoAt;

    @Column(length = 320)
    private String validadoPorEmail;

    @Column(name = "chofer_validacion_empleado_id")
    private Long choferValidacionEmpleadoId;

    @Column(length = 256)
    private String choferValidacionNombre;

    @OneToMany(mappedBy = "registroSalida", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RmRegistroSalidaDetalle> detalles = new ArrayList<>();

    @Formula("(select count(*) from rm_registro_salida_detalle d where d.registro_salida_id = id)")
    private long lineas;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(length = 320)
    private String createdByEmail;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
