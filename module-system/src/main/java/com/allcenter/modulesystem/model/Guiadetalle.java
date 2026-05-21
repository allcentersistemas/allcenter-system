package com.allcenter.modulesystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "guiadetalle")
@Getter
@Setter
@NoArgsConstructor
public class Guiadetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guia_id", nullable = false)
    private Guia guia;

    /** Si la línea proviene de un palé escaneado. */
    @Column(name = "pale_id")
    private Long paleId;

    @Column(name = "descripcion", nullable = false, length = 1024)
    private String descripcion;

    @Column(name = "unidad_medida", nullable = false, length = 64)
    private String unidadMedida;

    @Column(name = "cantidad", nullable = false, length = 64)
    private String cantidad;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;
}
