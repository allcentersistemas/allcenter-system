package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.ProyectoEstado;
import com.allcenter.modulesystem.model.ProyectoOptimizacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProyectoRepository extends JpaRepository<ProyectoOptimizacion, Long> {

    List<ProyectoOptimizacion> findByClientUserIdOrderByFechacreacionDesc(Long clientUserId);

    List<ProyectoOptimizacion> findByVendedorIdOrderByFechacreacionDesc(Long vendedorId);

    List<ProyectoOptimizacion> findAllByOrderByFechacreacionDesc();

    java.util.Optional<ProyectoOptimizacion> findFirstByClientUserIdAndNombreIgnoreCase(
            Long clientUserId, String nombre);
}
