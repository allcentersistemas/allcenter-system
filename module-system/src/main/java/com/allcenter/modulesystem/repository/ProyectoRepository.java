package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.ProyectoEstado;
import com.allcenter.modulesystem.model.ProyectoOptimizacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProyectoRepository extends JpaRepository<ProyectoOptimizacion, Long> {

    List<ProyectoOptimizacion> findByClientUserIdOrderByFechacreacionDesc(Long clientUserId);

    List<ProyectoOptimizacion> findByVendedorIdOrderByFechacreacionDesc(Long vendedorId);

    List<ProyectoOptimizacion> findAllByOrderByFechacreacionDesc();

    List<ProyectoOptimizacion> findByEstadoInOrderByFechacreacionDesc(List<ProyectoEstado> estados);

    @Query(
            """
            SELECT p.clientUserId,
                   COALESCE(NULLIF(TRIM(p.cliente), ''), 'Sin cliente'),
                   COUNT(o)
            FROM Orden o
            JOIN o.proyectoOptimizacionId p
            WHERE p.estado = :estado
            GROUP BY p.clientUserId, COALESCE(NULLIF(TRIM(p.cliente), ''), 'Sin cliente')
            ORDER BY COUNT(o) DESC
            """)
    List<Object[]> countOrdenesByClienteAndEstado(@Param("estado") ProyectoEstado estado);

    java.util.Optional<ProyectoOptimizacion> findFirstByClientUserIdAndNombreIgnoreCase(
            Long clientUserId, String nombre);
}
