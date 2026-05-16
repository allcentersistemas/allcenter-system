package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.RmActaConformidad;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RmActaConformidadRepository extends JpaRepository<RmActaConformidad, Long> {

    Page<RmActaConformidad> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
