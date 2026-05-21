package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.Guia;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuiaRepository extends JpaRepository<Guia, Long> {

    boolean existsByNumeroGuiaIgnoreCase(String numeroGuia);

    @Query(
            """
            SELECT g FROM Guia g
            JOIN FETCH g.transporte
            LEFT JOIN FETCH g.sucursalDestino
            LEFT JOIN FETCH g.ubicacionDestino
            ORDER BY g.fechaCreacion DESC
            """)
    List<Guia> findAllWithTransporte();

    @Query(
            """
            SELECT g FROM Guia g
            JOIN FETCH g.transporte
            LEFT JOIN FETCH g.sucursalDestino
            LEFT JOIN FETCH g.ubicacionDestino
            WHERE g.id = :id
            """)
    Optional<Guia> findByIdWithTransporte(@Param("id") Long id);
}
