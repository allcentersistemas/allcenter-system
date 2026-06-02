package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.InventoryDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientOptimizacionCatalogService {

    private final PlanillaCatalogService planillaCatalogService;

    @Transactional(readOnly = true)
    public InventoryDtos.OptimizacionKardexCatalog listKardexCatalog() {
        return planillaCatalogService.listClientCatalog();
    }
}
