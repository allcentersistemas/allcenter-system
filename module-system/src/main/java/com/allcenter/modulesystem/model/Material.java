package com.allcenter.modulesystem.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Table(name = "guia")
@Getter
@Setter
@NoArgsConstructor

public class Material {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "materialid")
    private Long id;
}
