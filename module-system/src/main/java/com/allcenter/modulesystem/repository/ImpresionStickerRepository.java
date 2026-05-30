package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.ImpresionSticker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ImpresionStickerRepository
        extends JpaRepository<ImpresionSticker, Long>, JpaSpecificationExecutor<ImpresionSticker> {}
