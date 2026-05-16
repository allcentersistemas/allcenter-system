package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.Orden;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrdenRepository extends JpaRepository<Orden, Long> {
    List<Orden> findByProyectoId_IdOrderByIdAsc(Long proyectoId);
}
