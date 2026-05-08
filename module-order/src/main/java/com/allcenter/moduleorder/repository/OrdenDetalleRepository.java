package com.allcenter.moduleorder.repository;

import com.allcenter.moduleorder.model.OrdenDetalle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrdenDetalleRepository extends JpaRepository<OrdenDetalle, Long> {
    List<OrdenDetalle> findByOrdenId_IdOrderByIdAsc(Long ordenId);

    void deleteByOrdenId_Id(Long ordenId);
}
