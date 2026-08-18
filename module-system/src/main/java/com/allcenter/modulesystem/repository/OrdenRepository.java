package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.Orden;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrdenRepository extends JpaRepository<Orden, Long> {
    List<Orden> findByProyectoOptimizacionId_IdOrderByIdAsc(Long proyectoId);

    void deleteByProyectoOptimizacionId_Id(Long proyectoId);

    @Query(
            """
            SELECT o FROM Orden o
            WHERE LOWER(TRIM(COALESCE(o.orderCode, ''))) = LOWER(TRIM(:q))
               OR LOWER(TRIM(COALESCE(o.orderName, ''))) = LOWER(TRIM(:q))
            """)
    List<Orden> findByCodeOrName(@Param("q") String q);
}
