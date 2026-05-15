package com.allcenter.modulerm.repository;

import com.allcenter.modulerm.entity.RmRegistroSalida;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RmRegistroSalidaRepository extends JpaRepository<RmRegistroSalida, Long> {

    Page<RmRegistroSalida> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query(
            """
            select distinct s from RmRegistroSalida s
            left join fetch s.detalles
            where s.id = :id
            """)
    Optional<RmRegistroSalida> findByIdWithDetalles(Long id);
}
