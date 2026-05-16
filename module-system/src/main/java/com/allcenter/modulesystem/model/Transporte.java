package com.allcenter.modulesystem.model;


import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "transporte")
@Getter
@Setter

public class Transporte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transporteid")
    private Long id;

    @Column(name = "placa")
    private String placa;

    @Column(name = "numeroserie")
    private String numeroserie;

    @Column(name = "marca")
    private String marca;

    @Column(name = "modelo")
    private String modelo;

    @Column(name = "color")
    private String color;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "tipovehiculo")
    private String tipoVehiculo;

    @Column(name = "capacidad")
    private Double capacidad;

    @Column(name = "activo")
    private Boolean activo;

    @Column(name = "fechacreacion")
    private LocalDateTime fechaCreacion;

}
