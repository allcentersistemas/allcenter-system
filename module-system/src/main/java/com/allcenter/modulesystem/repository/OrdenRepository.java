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
               OR LOWER(TRIM(COALESCE(o.biesseOrderName, ''))) = LOWER(TRIM(:q))
            """)
    List<Orden> findByCodeOrName(@Param("q") String q);

    List<Orden> findByBiesseOrderId(Long biesseOrderId);

    @Query(
            """
            SELECT o FROM Orden o
            WHERE o.opCodigo IS NOT NULL AND TRIM(o.opCodigo) <> ''
              AND o.proyectoOptimizacionId.id IN :proyectoIds
            ORDER BY o.opCodigo ASC, o.id ASC
            """)
    List<Orden> findLinkedByProyectoIds(@Param("proyectoIds") List<Long> proyectoIds);
}
