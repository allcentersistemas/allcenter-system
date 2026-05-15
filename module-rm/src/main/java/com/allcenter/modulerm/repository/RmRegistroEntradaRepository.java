package com.allcenter.modulerm.repository;

import com.allcenter.modulerm.entity.RmRegistroEntrada;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RmRegistroEntradaRepository extends JpaRepository<RmRegistroEntrada, Long> {

    Page<RmRegistroEntrada> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query(
            """
            select e from RmRegistroEntrada e
            left join fetch e.detalles
            where e.id = :id
            """)
    Optional<RmRegistroEntrada> findByIdWithDetalles(Long id);
}
