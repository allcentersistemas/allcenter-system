package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.Transporte;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransporteRepository extends JpaRepository<Transporte, Long> {
    Optional<Transporte> findByPlacaIgnoreCase(String placa);
}
