package com.allcenter.modulesystem.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "imgrs")
@Getter
@Setter
@NoArgsConstructor
public class ImgRS {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registro_salida_detalle_id", nullable = false)
    private RmRegistroSalidaDetalle registroEntrada;
}
