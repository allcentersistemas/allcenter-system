package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.RmRegistroSalida;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RmRegistroSalidaRepository extends JpaRepository<RmRegistroSalida, Long> {

    @EntityGraph(attributePaths = "registroVehiculo")
    Page<RmRegistroSalida> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select coalesce(max(s.numeroregistro), 0) from RmRegistroSalida s")
    int findMaxNumeroRegistro();

    @Query(
            """
            select distinct s from RmRegistroSalida s
            left join fetch s.detalles
            left join fetch s.registroVehiculo
            where s.id = :id
            """)
    Optional<RmRegistroSalida> findByIdWithDetalles(Long id);
}
