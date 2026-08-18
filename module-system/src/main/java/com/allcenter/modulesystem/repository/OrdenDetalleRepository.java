package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.OrdenDetalle;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrdenDetalleRepository extends JpaRepository<OrdenDetalle, Long> {
    List<OrdenDetalle> findByOrdenId_IdOrderByIdAsc(Long ordenId);

    void deleteByOrdenId_Id(Long ordenId);

    @Query(
            """
            SELECT o.proyectoOptimizacionId.id, COALESCE(SUM(d.cantidad), 0)
            FROM OrdenDetalle d
            JOIN d.ordenId o
            WHERE o.proyectoOptimizacionId.id IN :proyectoIds
            GROUP BY o.proyectoOptimizacionId.id
            """)
    List<Object[]> sumCantidadByProyectoIds(@Param("proyectoIds") Collection<Long> proyectoIds);
}
