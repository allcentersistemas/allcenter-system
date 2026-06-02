package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.InventoryDtos;
import com.allcenter.modulesystem.model.InvItem;
import com.allcenter.modulesystem.repository.InvItemRepository;
import com.allcenter.modulesystem.repository.InvStockMovementRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientOptimizacionCatalogService {

    private static final int CATALOG_LIMIT = 500;

    private final InvItemRepository itemRepository;
    private final InvStockMovementRepository movementRepository;

    @Transactional(readOnly = true)
    public InventoryDtos.OptimizacionKardexCatalog listKardexCatalog() {
        List<InventoryDtos.KardexMaterialOption> tableros =
                mapOptions(InventoryApplicationService.FAMILIA_TABLERO);
        List<InventoryDtos.KardexMaterialOption> cantos =
                mapOptions(InventoryApplicationService.FAMILIA_CANTO);
        return new InventoryDtos.OptimizacionKardexCatalog(tableros, cantos);
    }

    private List<InventoryDtos.KardexMaterialOption> mapOptions(String familia) {
        return itemRepository
                .findActiveCatalogByFamilia(familia, PageRequest.of(0, CATALOG_LIMIT))
                .stream()
                .map(this::toOption)
                .toList();
    }

    private InventoryDtos.KardexMaterialOption toOption(InvItem item) {
        BigDecimal stock =
                movementRepository.sumQuantityChangeByItemId(item.getId());
        if (stock == null) {
            stock = BigDecimal.ZERO;
        }
        return new InventoryDtos.KardexMaterialOption(
                item.getId(),
                item.getSku(),
                item.getName(),
                item.getUnit(),
                stock);
    }
}
