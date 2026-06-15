package com.allcenter.modulesystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "maquina_optimizacion")
@Getter
@Setter
public class MaquinaOptimizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maquinaid")
    private Long id;

    /** Valor exportado en P_PARAMS (ej. DEF - SEKTOR470). */
    @Column(name = "codigo", nullable = false, unique = true, length = 128)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 256)
    private String nombre;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
