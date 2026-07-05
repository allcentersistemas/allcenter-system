package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.RmRegistroSalida;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RmRegistroSalidaRepository extends JpaRepository<RmRegistroSalida, Long> {

    @EntityGraph(attributePaths = "registroVehiculo")
    Page<RmRegistroSalida> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "registroVehiculo")
    @Query(
            """
            SELECT DISTINCT s FROM RmRegistroSalida s
            LEFT JOIN s.registroVehiculo v
            WHERE (:fechaDesde IS NULL OR s.fecha >= :fechaDesde)
              AND (:fechaHasta IS NULL OR s.fecha <= :fechaHasta)
              AND (:tipoRegistro IS NULL OR LOWER(v.tiporegistro) = LOWER(:tipoRegistro))
              AND (
                :q IS NULL OR (
                    CONCAT('', s.numeroregistro, '') LIKE CONCAT('%', :q, '%') OR
                    LOWER(COALESCE(s.numeroGuia, '')) LIKE CONCAT('%', :q, '%') OR
                    LOWER(COALESCE(s.ocNumero, '')) LIKE CONCAT('%', :q, '%') OR
                    LOWER(COALESCE(v.placa, '')) LIKE CONCAT('%', :q, '%') OR
                    LOWER(COALESCE(v.chofer, '')) LIKE CONCAT('%', :q, '%') OR
                    LOWER(COALESCE(v.marca, '')) LIKE CONCAT('%', :q, '%') OR
                    EXISTS (
                        SELECT 1 FROM Guia g WHERE g.id = s.guiaInventarioId AND (
                            LOWER(g.numeroGuia) LIKE CONCAT('%', :q, '%') OR
                            LOWER(COALESCE(g.ordenCompra, '')) LIKE CONCAT('%', :q, '%')
                        )
                    )
                )
              )
            ORDER BY s.createdAt DESC
            """)
    Page<RmRegistroSalida> pageFiltered(
            @Param("q") String q,
            @Param("fechaDesde") LocalDate fechaDesde,
            @Param("fechaHasta") LocalDate fechaHasta,
            @Param("tipoRegistro") String tipoRegistro,
            Pageable pageable);

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
