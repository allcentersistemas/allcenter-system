package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.MaquinaOptimizacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaquinaOptimizacionRepository extends JpaRepository<MaquinaOptimizacion, Long> {

    List<MaquinaOptimizacion> findByActivoTrueOrderByNombreAsc();

    List<MaquinaOptimizacion> findAllByOrderByNombreAsc();
}
