package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.RmRegistroEntrada;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RmRegistroEntradaRepository extends JpaRepository<RmRegistroEntrada, Long> {

    @EntityGraph(attributePaths = "registroVehiculo")
    Page<RmRegistroEntrada> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "registroVehiculo")
    @Query(
            """
            SELECT DISTINCT e FROM RmRegistroEntrada e
            LEFT JOIN e.registroVehiculo v
            WHERE (
                LOWER(CAST(e.numeroregistro AS string)) LIKE CONCAT('%', :q, '%') OR
                LOWER(COALESCE(e.ocNumero, '')) LIKE CONCAT('%', :q, '%') OR
                LOWER(COALESCE(e.guiaNumero, '')) LIKE CONCAT('%', :q, '%') OR
                LOWER(COALESCE(v.placa, '')) LIKE CONCAT('%', :q, '%') OR
                LOWER(COALESCE(v.chofer, '')) LIKE CONCAT('%', :q, '%') OR
                LOWER(COALESCE(v.marca, '')) LIKE CONCAT('%', :q, '%') OR
                LOWER(COALESCE(v.guiaNumero, '')) LIKE CONCAT('%', :q, '%') OR
                LOWER(COALESCE(v.ocNumero, '')) LIKE CONCAT('%', :q, '%') OR
                EXISTS (
                    SELECT 1 FROM Guia g WHERE g.id = e.guiaInventarioId AND (
                        LOWER(g.numeroGuia) LIKE CONCAT('%', :q, '%') OR
                        LOWER(COALESCE(g.ordenCompra, '')) LIKE CONCAT('%', :q, '%')
                    )
                )
            )
            ORDER BY e.createdAt DESC
            """)
    Page<RmRegistroEntrada> searchByTerm(@Param("q") String q, Pageable pageable);

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
