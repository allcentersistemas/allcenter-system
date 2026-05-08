package com.allcenter.modulebiesse.repository;

import com.allcenter.modulebiesse.model.ImpresionStickerDetalle;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImpresionStickerDetalleRepository
        extends JpaRepository<ImpresionStickerDetalle, Long> {

    List<ImpresionStickerDetalle> findByImpresion_IdOrderByIdAsc(Long impresionId);
}
