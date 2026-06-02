package com.allcenter.modulesystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tablero")
@Getter
@Setter
@NoArgsConstructor
public class Tablero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tableroid")
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String codigo;

    @Column(nullable = false, length = 512)
    private String nombre;

    @Column(name = "espesor_mm")
    private Integer espesorMm;

    @Column(nullable = false, length = 32)
    private String unidad = "PLN";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
