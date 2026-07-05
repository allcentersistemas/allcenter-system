package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.RmActaConformidad;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RmActaConformidadRepository extends JpaRepository<RmActaConformidad, Long> {

    @Query(
            """
            SELECT a FROM RmActaConformidad a
            WHERE (:desde IS NULL OR a.createdAt >= :desde)
              AND (:hasta IS NULL OR a.createdAt <= :hasta)
              AND (
                :q IS NULL OR (
                    LOWER(CAST(COALESCE(a.razonSocialNombre, '') AS string)) LIKE CONCAT('%', :q, '%') OR
                    LOWER(CAST(COALESCE(a.decision, '') AS string)) LIKE CONCAT('%', :q, '%')
                )
              )
            ORDER BY a.createdAt DESC
            """)
    Page<RmActaConformidad> pageFiltered(
            @Param("q") String q,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta,
            Pageable pageable);
}
