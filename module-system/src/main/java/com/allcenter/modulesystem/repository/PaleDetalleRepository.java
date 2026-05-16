package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.PaleDetalle;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaleDetalleRepository extends JpaRepository<PaleDetalle, Long> {
    List<PaleDetalle> findByPale_IdOrderByFechaAgregadoDesc(Long paleId);

    Optional<PaleDetalle> findByPale_IdAndPiezaId(Long paleId, Long piezaId);
}
