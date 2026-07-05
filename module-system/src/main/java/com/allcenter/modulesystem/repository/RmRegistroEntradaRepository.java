package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.RmRegistroEntrada;
import java.time.LocalDate;
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
            WHERE (:fechaDesde IS NULL OR e.fecha >= :fechaDesde)
              AND (:fechaHasta IS NULL OR e.fecha <= :fechaHasta)
              AND (:tipoRegistro IS NULL OR LOWER(v.tiporegistro) = LOWER(:tipoRegistro))
              AND (
                :q IS NULL OR (
                    CONCAT('', e.numeroregistro, '') LIKE CONCAT('%', :q, '%') OR
                    LOWER(COALESCE(e.ocNumero, '')) LIKE CONCAT('%', :q, '%') OR
                    LOWER(COALESCE(e.numeroGuia, '')) LIKE CONCAT('%', :q, '%') OR
                    LOWER(COALESCE(v.placa, '')) LIKE CONCAT('%', :q, '%') OR
                    LOWER(COALESCE(v.chofer, '')) LIKE CONCAT('%', :q, '%') OR
                    LOWER(COALESCE(v.marca, '')) LIKE CONCAT('%', :q, '%') OR
                    EXISTS (
                        SELECT 1 FROM Guia g WHERE g.id = e.guiaInventarioId AND (
                            LOWER(g.numeroGuia) LIKE CONCAT('%', :q, '%') OR
                            LOWER(COALESCE(g.ordenCompra, '')) LIKE CONCAT('%', :q, '%')
                        )
                    )
                )
              )
            ORDER BY e.createdAt DESC
            """)
    Page<RmRegistroEntrada> pageFiltered(
            @Param("q") String q,
            @Param("fechaDesde") LocalDate fechaDesde,
            @Param("fechaHasta") LocalDate fechaHasta,
            @Param("tipoRegistro") String tipoRegistro,
            Pageable pageable);

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
