package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.TransporteCargaDetalle;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransporteCargaDetalleRepository extends JpaRepository<TransporteCargaDetalle, Long> {
    List<TransporteCargaDetalle> findByTransporteCargaIdOrderByFechaRegistroDesc(Long transporteCargaId);

    boolean existsByTransporteCargaIdAndPaleEnvioId(Long transporteCargaId, Long paleEnvioId);

    long countByTransporteCargaId(Long transporteCargaId);
}
