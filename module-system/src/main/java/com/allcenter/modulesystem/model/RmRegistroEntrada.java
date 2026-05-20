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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Formula;

@Entity
@Table(name = "rm_registro_entrada")
@Getter
@Setter
@NoArgsConstructor
public class RmRegistroEntrada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registro_vehiculo_id", nullable = false)
    private RmRegistroVehiculo registroVehiculo;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 16)
    private String hora;

    /** OC (orden de compra) o NG (numero de guia). */
    @Column(name = "tipo_documento", nullable = false, length = 8)
    private String tipoDocumento;

    @Column(length = 128)
    private String ocNumero;

    @Column(length = 128)
    private String guiaNumero;

    /** Fotos del documento (OC o guia). Reutiliza columna legacy de cabecera. */
    @Column(name = "cabecera_vehiculo_photo_filenames_json", columnDefinition = "TEXT")
    private String documentoPhotoFilenamesJson;

    @Column(length = 32)
    private String recepcionEstado;

    private Instant validadoAt;

    @Column(length = 320)
    private String validadoPorEmail;

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
    private String estadoEntrega;

    @Column(length = 256)
    private String estadoRuta;

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
