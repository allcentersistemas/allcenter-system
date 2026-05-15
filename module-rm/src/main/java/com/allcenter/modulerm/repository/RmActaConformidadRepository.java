package com.allcenter.modulerm.repository;

import com.allcenter.modulerm.entity.RmActaConformidad;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RmActaConformidadRepository extends JpaRepository<RmActaConformidad, Long> {

    Page<RmActaConformidad> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
