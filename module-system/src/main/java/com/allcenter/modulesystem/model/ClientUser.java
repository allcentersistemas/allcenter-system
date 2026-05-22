package com.allcenter.modulesystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "client_users")
@Getter
@Setter
public class ClientUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(nullable = false, length = 180)
    private String displayName;

    @Column(length = 180)
    private String nombre;

    @Column(length = 40)
    private String phone;

    @Column(length = 40)
    private String documentodeindentificacion;

    @Column(nullable = false)
    private String password;

    @Column(length = 200)
    private String direccion;

    @Column(length = 200)
    private String distrito;

    @Column(length = 200)
    private String departamento;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean juridica;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
