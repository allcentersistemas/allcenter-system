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

/**
 * Acta capturada desde el móvil (pantalla "no conformidad"; el path de API conserva el nombre legacy
 * {@code actas-conformidad}).
 */
@Entity
@Table(name = "rm_acta_conformidad")
@Getter
@Setter
@NoArgsConstructor
public class RmActaConformidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 512)
    private String razonSocialNombre;

    @Column(length = 128)
    private String guiaRemisionNum;

    @Column(length = 128)
    private String facturaOrdenCompraNum;

    @Column(length = 512)
    private String transportistaNombrePlaca;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String tiposJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcionAmpliada;

    @Column(nullable = false, length = 64)
    private String decision;

    private Integer cantidadConformeUnidades;

    @Column(columnDefinition = "TEXT")
    private String observacionesDecision;

    @Column(columnDefinition = "TEXT")
    private String photoFilenamesJson;

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
