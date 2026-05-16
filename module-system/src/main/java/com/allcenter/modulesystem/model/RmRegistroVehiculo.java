package com.allcenter.modulesystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rm_registro_vehiculo")
@Getter
@Setter
@NoArgsConstructor
public class RmRegistroVehiculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(length = 16)
    private String horaIngreso;

    @Column(nullable = false, length = 128)
    private String marca;

    @Column(nullable = false, length = 32)
    private String placa;

    @Column(nullable = false, length = 256)
    private String chofer;

    @Column(length = 32)
    private String kilometraje;

    @Column(length = 16)
    private String horaSalida;

    @Column(columnDefinition = "TEXT")
    private String photoFilenamesJson;

    /** Lista JSON de productos transportados (material, cantidad, unidad). */
    @Column(columnDefinition = "TEXT")
    private String productosJson;

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
