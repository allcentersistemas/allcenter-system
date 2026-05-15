package com.allcenter.modulerm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "imgre")
@Getter
@Setter
@NoArgsConstructor

public class ImgRE {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long Id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registro_entrada_detalle_id", nullable = false)
    private RmRegistroEntradaDetalle registroEntradaDetalle;

}
