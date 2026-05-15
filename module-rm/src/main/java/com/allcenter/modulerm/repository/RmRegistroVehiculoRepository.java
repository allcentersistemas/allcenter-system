package com.allcenter.modulerm.repository;

import com.allcenter.modulerm.entity.RmRegistroVehiculo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RmRegistroVehiculoRepository extends JpaRepository<RmRegistroVehiculo, Long> {

    Page<RmRegistroVehiculo> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
