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
@Table(name = "inv_item")
@Getter
@Setter
@NoArgsConstructor
public class InvItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false, length = 512)
    private String name;

    @Column(nullable = false, length = 32)
    private String unit = "UN";

    @Column(nullable = false)
    private boolean active = true;

    /** TABLERO | CANTO — catálogo para planilla de corte (portal cliente). */
    @Column(name = "familia_codigo", length = 32)
    private String familiaCodigo;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
