package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.Guia;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuiaRepository extends JpaRepository<Guia, Long> {

    @Query(
            value =
                    """
                    SELECT COALESCE(MAX(CAST(SUBSTRING(numero_guia FROM 3) AS BIGINT)), 0)
                    FROM guia
                    WHERE numero_guia ~ '^G-[0-9]+$'
                    """,
            nativeQuery = true)
    Long findMaxCorrelativoSequence();

    @Query(
            """
            SELECT g FROM Guia g
            LEFT JOIN FETCH g.sucursalOrigen
            LEFT JOIN FETCH g.sucursalDestino
            LEFT JOIN FETCH g.ubicacionDestino
            ORDER BY g.fechaCreacion DESC
            """)
    List<Guia> findAllForList();

    @Query(
            """
            SELECT DISTINCT g FROM Guia g
            LEFT JOIN FETCH g.sucursalOrigen
            LEFT JOIN FETCH g.sucursalDestino
            LEFT JOIN FETCH g.ubicacionDestino
            LEFT JOIN FETCH g.detalles
            WHERE g.id = :id
            """)
    Optional<Guia> findByIdWithDetalles(@Param("id") Long id);
}
