package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.RmRegistroEntrada;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RmRegistroEntradaRepository extends JpaRepository<RmRegistroEntrada, Long> {

    @EntityGraph(attributePaths = "registroVehiculo")
    Page<RmRegistroEntrada> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<RmRegistroEntrada> findByRegistroVehiculoIdOrderByCreatedAtDesc(Long registroVehiculoId);

    @Query("select coalesce(max(e.numeroregistro), 0) from RmRegistroEntrada e")
    int findMaxNumeroRegistro();

    @Query(
            """
            select e from RmRegistroEntrada e
            left join fetch e.detalles
            left join fetch e.registroVehiculo
            where e.id = :id
            """)
    Optional<RmRegistroEntrada> findByIdWithDetalles(Long id);
}
