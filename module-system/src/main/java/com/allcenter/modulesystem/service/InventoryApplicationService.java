package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.InventoryDtos;
import com.allcenter.modulesystem.model.InvItem;
import com.allcenter.modulesystem.model.InvStockMovement;
import com.allcenter.modulesystem.repository.InvItemRepository;
import com.allcenter.modulesystem.repository.InvStockMovementRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
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

    private static final String UNIT_PIEZAS = "piezas";
    private static final Pattern SKU_SAFE = Pattern.compile("[^A-Za-z0-9]+");

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

    /**
     * Acredita stock en piezas por cada línea recibida en un ingreso RM validado.
     *
     * @param lines material + cantidad por línea
     * @param entradaId id de {@code rm_registro_entrada} (referencia en movimiento)
     */
    @Transactional
    public void creditStockFromRmIngreso(
            List<RmIngresoStockLine> lines, long entradaId, String createdByEmail) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        String ref = "rm_entrada:" + entradaId;
        for (RmIngresoStockLine line : lines) {
            String material = line.material() == null ? "" : line.material().trim();
            if (material.isEmpty()) {
                continue;
            }
            BigDecimal qty = parsePositiveQuantity(line.cantidad());
            if (qty == null) {
                throw new ResponseStatusException(
                        BAD_REQUEST, "Cantidad invalida para inventario: " + material);
            }
            long itemId = findOrCreatePiezasItem(material);
            addMovement(
                    itemId,
                    new InventoryDtos.CreateMovementRequest(
                            qty, "Ingreso RM (recepción mercadería)", ref),
                    createdByEmail);
        }
    }

    public record RmIngresoStockLine(String material, String cantidad) {}

    private long findOrCreatePiezasItem(String materialName) {
        String sku = toSku(materialName);
        return itemRepository
                .findBySkuIgnoreCase(sku)
                .map(InvItem::getId)
                .orElseGet(
                        () -> {
                            InvItem i = new InvItem();
                            i.setSku(sku);
                            i.setName(materialName.length() > 512 ? materialName.substring(0, 512) : materialName);
                            i.setUnit(UNIT_PIEZAS);
                            i.setActive(true);
                            try {
                                return itemRepository.save(i).getId();
                            } catch (DataIntegrityViolationException e) {
                                return itemRepository
                                        .findBySkuIgnoreCase(sku)
                                        .map(InvItem::getId)
                                        .orElseThrow(
                                                () ->
                                                        new ResponseStatusException(
                                                                CONFLICT, "No se pudo crear articulo de inventario", e));
                            }
                        });
    }

    private static String toSku(String materialName) {
        String base = SKU_SAFE.matcher(materialName.trim().toUpperCase(Locale.ROOT)).replaceAll("-");
        if (base.isEmpty()) {
            base = "MAT";
        }
        if (base.length() > 60) {
            base = base.substring(0, 60);
        }
        return "RM-" + base;
    }

    private static BigDecimal parsePositiveQuantity(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim().replace(',', '.');
        try {
            BigDecimal v = new BigDecimal(t);
            return v.compareTo(BigDecimal.ZERO) > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
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
