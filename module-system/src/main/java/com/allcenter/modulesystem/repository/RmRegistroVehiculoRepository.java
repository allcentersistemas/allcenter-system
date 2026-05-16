package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.RmRegistroVehiculo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RmRegistroVehiculoRepository extends JpaRepository<RmRegistroVehiculo, Long> {

    Page<RmRegistroVehiculo> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
