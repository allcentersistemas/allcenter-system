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
@Table(name = "rm_registro_entrada")
@Getter
@Setter
@NoArgsConstructor
public class RmRegistroEntrada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 16)
    private String hora;

    /** Referencia al vehículo en module-system (sin FK JPA entre módulos). */
    @Column(name = "transporte_id")
    private Long transporteId;

    /** Fotos del vehículo al momento del ingreso (JSON lista de nombres de archivo). */
    @Column(name = "cabecera_vehiculo_photo_filenames_json", columnDefinition = "TEXT")
    private String cabeceraVehiculoPhotoFilenamesJson;

    /** Empleado con rol CHOFER que ingresa la mercadería (module-system id). */
    @Column(name = "chofer_ingreso_empleado_id")
    private Long choferIngresoEmpleadoId;

    @Column(length = 256)
    private String choferIngresoNombre;

    @Column(length = 32)
    private String kilometrajeIngreso;

    /** VALIDADO cuando se cerró recepción con conformidad; null en registros antiguos. */
    @Column(length = 32)
    private String recepcionEstado;

    private Instant validadoAt;

    @Column(length = 320)
    private String validadoPorEmail;

    /** Empleado CHOFER que validó la conformidad (id + nombre al momento del registro). */
    @Column(name = "chofer_validacion_empleado_id")
    private Long choferValidacionEmpleadoId;

    @Column(length = 256)
    private String choferValidacionNombre;

    @OneToMany(mappedBy = "registroEntrada", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RmRegistroEntradaDetalle> detalles = new ArrayList<>();

    @Formula(
            "(select count(*) from rm_registro_entrada_detalle d where d.registro_entrada_id = id)")
    private long lineas;

    @Column(length = 256)
    private String EstadoEntrega;

    @Column(length = 256)
    private String EstadoRuta;

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
