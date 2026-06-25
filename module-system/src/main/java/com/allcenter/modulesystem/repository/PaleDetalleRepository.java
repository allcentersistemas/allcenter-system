package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.PaleDetalle;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaleDetalleRepository extends JpaRepository<PaleDetalle, Long> {
    List<PaleDetalle> findByPale_IdOrderByFechaAgregadoDesc(Long paleId);

    Optional<PaleDetalle> findByPale_IdAndPiezaId(Long paleId, Long piezaId);

    Optional<PaleDetalle> findFirstByPiezaId(Long piezaId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT d.pale.id FROM PaleDetalle d WHERE d.orderId = :orderId")
    java.util.List<Long> findDistinctPaleIdsByOrderId(
            @org.springframework.data.repository.query.Param("orderId") Long orderId);
}
