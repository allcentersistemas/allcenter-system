package com.allcenter.modulesystem.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ubicacion")
@Getter
@Setter
public class Ubicacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ubicacionid")
    private Long id;

    @Column
    private String nombre;

    @Column
    private String direccion;

    @Column
    private String distrito;

    @Column
    private String departamento;

    @Column
    private String ciudad;

    @Column
    private String estado;

}