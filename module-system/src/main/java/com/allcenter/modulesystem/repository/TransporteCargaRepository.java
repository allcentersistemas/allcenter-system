package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.TransporteCarga;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransporteCargaRepository extends JpaRepository<TransporteCarga, Long> {

    @Query(
            """
            SELECT tc
            FROM TransporteCarga tc
            LEFT JOIN FETCH tc.transporte
            """)
    List<TransporteCarga> findAllWithTransporte();

    @Query(
            """
            SELECT tc
            FROM TransporteCarga tc
            LEFT JOIN FETCH tc.transporte
            WHERE tc.id = :id
            """)
    Optional<TransporteCarga> findByIdWithTransporte(@Param("id") Long id);
}
