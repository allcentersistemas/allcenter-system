package com.allcenter.modulerm.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "imgrv")
@Getter
@Setter
@NoArgsConstructor
public class ImgRV {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registro_vehiculo_detalle_id", nullable = false)
    private RmRegistroVehiculo registroEntrada;
}
