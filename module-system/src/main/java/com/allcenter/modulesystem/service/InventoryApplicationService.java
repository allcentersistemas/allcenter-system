package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.InventoryDtos;
import com.allcenter.modulesystem.model.InvItem;
import com.allcenter.modulesystem.model.InvStockMovement;
import com.allcenter.modulesystem.repository.InvItemRepository;
import com.allcenter.modulesystem.repository.InvStockMovementRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class InventoryApplicationService {

    private final InvItemRepository itemRepository;
    private final InvStockMovementRepository movementRepository;

    @Transactional(readOnly = true)
    public Page<InventoryDtos.ItemRow> pageItems(String q, Pageable pageable) {
        Page<InvItem> page;
        if (q == null || q.isBlank()) {
            page = itemRepository.findByActiveTrue(pageable);
        } else {
            page = itemRepository.searchActive(q.trim(), pageable);
        }
        return page.map(this::toRow);
    }

    @Transactional
    public long createItem(InventoryDtos.CreateItemRequest req, String createdByEmail) {
        if (itemRepository.findBySkuIgnoreCase(req.sku().trim()).isPresent()) {
            throw new ResponseStatusException(CONFLICT, "SKU ya existe");
        }
        InvItem i = new InvItem();
        i.setSku(req.sku().trim());
        i.setName(req.name().trim());
        String u = req.unit() == null || req.unit().isBlank() ? "UN" : req.unit().trim();
        i.setUnit(u.length() > 32 ? u.substring(0, 32) : u);
        i.setActive(true);
        try {
            return itemRepository.save(i).getId();
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(CONFLICT, "SKU duplicado", e);
        }
    }

    @Transactional(readOnly = true)
    public InventoryDtos.ItemDetail getItemDetail(long id) {
        InvItem item =
                itemRepository.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Artículo no encontrado"));
        BigDecimal balance = movementRepository.sumQuantityChangeByItemId(id);
        Page<InvStockMovement> moves = movementRepository.findByItem_IdOrderByCreatedAtDesc(id, PageRequest.of(0, 50));
        return new InventoryDtos.ItemDetail(
                toRow(item),
                balance,
                moves.getContent().stream().map(this::toMovementRow).toList());
    }

    @Transactional
    public long addMovement(long itemId, InventoryDtos.CreateMovementRequest req, String createdByEmail) {
        InvItem item =
                itemRepository.findById(itemId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Artículo no encontrado"));
        if (!item.isActive()) {
            throw new ResponseStatusException(BAD_REQUEST, "Artículo inactivo");
        }
        BigDecimal delta = req.quantityChange();
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "quantityChange no puede ser cero");
        }
        InvStockMovement m = new InvStockMovement();
        m.setItem(item);
        m.setQuantityChange(delta);
        m.setReason(req.reason().trim());
        m.setExternalRef(trimNullable(req.externalRef(), 128));
        m.setCreatedByEmail(trimNullable(createdByEmail, 320));
        return movementRepository.save(m).getId();
    }

    private static String trimNullable(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() <= max ? t : t.substring(0, max);
    }

    private InventoryDtos.ItemRow toRow(InvItem i) {
        return new InventoryDtos.ItemRow(i.getId(), i.getSku(), i.getName(), i.getUnit(), i.isActive(), i.getCreatedAt());
    }

    private InventoryDtos.MovementRow toMovementRow(InvStockMovement m) {
        return new InventoryDtos.MovementRow(
                m.getId(),
                m.getQuantityChange(),
                m.getReason(),
                m.getExternalRef(),
                m.getCreatedAt(),
                m.getCreatedByEmail());
    }
}
