package com.allcenter.modulelocation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sucursal")
@Getter
@Setter
public class Sucursal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sucursalid")
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 250)
    private String direccion;

    @Column(length = 120)
    private String ciudad;

    @Column(length = 120)
    private String departamento;
}
