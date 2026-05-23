package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.InventoryDtos;
import com.allcenter.modulesystem.model.InvItem;
import com.allcenter.modulesystem.model.InvStockMovement;
import com.allcenter.modulesystem.model.Pale;
import com.allcenter.modulesystem.model.PaleDetalle;
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

    public static final String CAT_DISPONIBLE = "DISPONIBLE";
    public static final String CAT_MERCA = "MERCA";
    public static final String CAT_REUTILIZABLE = "REUTILIZABLE";

    private static final String UNIT_PIEZAS = "piezas";
    private static final Pattern SKU_SAFE = Pattern.compile("[^A-Za-z0-9]+");

    private final InvItemRepository itemRepository;
    private final InvStockMovementRepository movementRepository;

    public List<InventoryDtos.CategoriaRow> listCategorias() {
        return List.of(
                new InventoryDtos.CategoriaRow(CAT_DISPONIBLE, "Disponible"),
                new InventoryDtos.CategoriaRow(CAT_MERCA, "Merma / merca"),
                new InventoryDtos.CategoriaRow(CAT_REUTILIZABLE, "Reutilizable"));
    }

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
    public InventoryDtos.ItemDetail getItemDetail(long id, Long sucursalId) {
        InvItem item =
                itemRepository.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Artículo no encontrado"));
        BigDecimal balance =
                sucursalId != null
                        ? movementRepository.sumQuantityChangeByItemIdSucursalCategoria(id, sucursalId, null)
                        : movementRepository.sumQuantityChangeByItemId(id);
        List<InventoryDtos.BalanceByCategoria> balancesByCategoria = new java.util.ArrayList<>();
        if (sucursalId != null) {
            for (InventoryDtos.CategoriaRow cat : listCategorias()) {
                BigDecimal b =
                        movementRepository.sumQuantityChangeByItemIdSucursalCategoria(
                                id, sucursalId, cat.codigo());
                balancesByCategoria.add(
                        new InventoryDtos.BalanceByCategoria(cat.codigo(), cat.etiqueta(), b));
            }
        }
        Page<InvStockMovement> moves = movementRepository.findByItem_IdOrderByCreatedAtDesc(id, PageRequest.of(0, 50));
        List<InvStockMovement> filtered =
                sucursalId == null
                        ? moves.getContent()
                        : moves.getContent().stream()
                                .filter(m -> sucursalId.equals(m.getSucursalId()))
                                .toList();
        return new InventoryDtos.ItemDetail(
                toRow(item),
                balance,
                sucursalId,
                balancesByCategoria,
                filtered.stream().map(this::toMovementRow).toList());
    }

    @Transactional
    public long addMovement(long itemId, InventoryDtos.CreateMovementRequest req, String createdByEmail) {
        return addMovementInternal(
                itemId,
                req.quantityChange(),
                req.reason(),
                req.externalRef(),
                req.sucursalId(),
                normalizeCategoria(req.categoriaCodigo()),
                req.observaciones(),
                createdByEmail,
                req.sucursalId() != null);
    }

    /**
     * Registra el palé en el almacén de la sucursal (unidad logística).
     */
    @Transactional
    public void registerPaleInWarehouse(Pale pale, Long sucursalId, String createdByEmail) {
        if (sucursalId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "El empleado debe tener sucursal asignada para registrar el palé en almacén");
        }
        String ref = "pale_open:" + pale.getId();
        if (movementRepository.existsByExternalRef(ref)) {
            return;
        }
        String sku = "PALET-" + pale.getCodigo();
        long itemId = findOrCreateItem(sku, "Palé " + pale.getCodigo(), UNIT_PIEZAS);
        addMovementInternal(
                itemId,
                BigDecimal.ONE,
                "Registro de palé en almacén",
                ref,
                sucursalId,
                CAT_DISPONIBLE,
                pale.getNotas(),
                createdByEmail,
                false);
    }

    /** Acredita piezas escaneadas al cerrar el palé en la sucursal correspondiente. */
    @Transactional
    public void creditStockFromPaleClose(
            Pale pale, List<PaleDetalle> detalles, Long sucursalId, String createdByEmail) {
        if (sucursalId == null || detalles == null || detalles.isEmpty()) {
            return;
        }
        String refBase = "pale_close:" + pale.getId() + ":";
        for (PaleDetalle d : detalles) {
            String material = resolvePalePieceMaterial(d);
            if (material.isEmpty()) {
                continue;
            }
            String ref = refBase + d.getId();
            if (movementRepository.existsByExternalRef(ref)) {
                continue;
            }
            long itemId = findOrCreateItem(toSku(material), material, UNIT_PIEZAS);
            addMovementInternal(
                    itemId,
                    BigDecimal.ONE,
                    "Ingreso por cierre de palé " + pale.getCodigo(),
                    ref,
                    sucursalId,
                    CAT_DISPONIBLE,
                    null,
                    createdByEmail,
                    false);
        }
    }

    @Transactional
    public void creditStockFromRmIngreso(
            List<StockLine> lines, long entradaId, Long sucursalId, String createdByEmail) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        if (sucursalId == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Sucursal del usuario requerida para acreditar inventario en ingreso");
        }
        String refPrefix = "rm_entrada:" + entradaId + ":";
        int i = 0;
        for (StockLine line : lines) {
            applyStockDelta(line, refPrefix + i++, sucursalId, true, "Ingreso RM (recepción mercadería)", createdByEmail);
        }
    }

    @Transactional
    public void debitStockFromRmSalida(
            List<StockLine> lines, long salidaId, Long sucursalId, String createdByEmail) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        if (sucursalId == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Sucursal del usuario requerida para descontar inventario en salida");
        }
        String refPrefix = "rm_salida:" + salidaId + ":";
        int i = 0;
        for (StockLine line : lines) {
            applyStockDelta(line, refPrefix + i++, sucursalId, false, "Salida RM (despacho mercadería)", createdByEmail);
        }
    }

    public record StockLine(String material, String cantidad, String categoriaCodigo, String observaciones) {}

    /** Compatibilidad con llamadas previas sin categoría. */
    public record RmIngresoStockLine(String material, String cantidad) {
        public StockLine toStockLine() {
            return new StockLine(material, cantidad, CAT_DISPONIBLE, null);
        }
    }

    private void applyStockDelta(
            StockLine line,
            String externalRef,
            Long sucursalId,
            boolean credit,
            String reason,
            String createdByEmail) {
        if (movementRepository.existsByExternalRef(externalRef)) {
            return;
        }
        String material = line.material() == null ? "" : line.material().trim();
        if (material.isEmpty()) {
            return;
        }
        BigDecimal qty = parsePositiveQuantity(line.cantidad());
        if (qty == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Cantidad invalida para inventario: " + material);
        }
        String categoria = normalizeCategoria(line.categoriaCodigo());
        long itemId = findOrCreateItem(toSku(material), material, UNIT_PIEZAS);
        BigDecimal delta = credit ? qty : qty.negate();
        addMovementInternal(
                itemId,
                delta,
                reason,
                externalRef,
                sucursalId,
                categoria,
                line.observaciones(),
                createdByEmail,
                !credit);
    }

    private long addMovementInternal(
            long itemId,
            BigDecimal delta,
            String reason,
            String externalRef,
            Long sucursalId,
            String categoriaCodigo,
            String observaciones,
            String createdByEmail,
            boolean checkBalance) {
        InvItem item =
                itemRepository.findById(itemId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Artículo no encontrado"));
        if (!item.isActive()) {
            throw new ResponseStatusException(BAD_REQUEST, "Artículo inactivo");
        }
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "quantityChange no puede ser cero");
        }
        String cat = normalizeCategoria(categoriaCodigo);
        if (checkBalance && sucursalId != null && delta.signum() < 0) {
            BigDecimal onHand =
                    movementRepository.sumQuantityChangeByItemIdSucursalCategoria(itemId, sucursalId, cat);
            if (onHand.add(delta).compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "Stock insuficiente en sucursal para "
                                + item.getSku()
                                + " (categoría "
                                + cat
                                + ")");
            }
        }
        InvStockMovement m = new InvStockMovement();
        m.setItem(item);
        m.setQuantityChange(delta);
        m.setReason(reason.trim());
        m.setExternalRef(trimNullable(externalRef, 128));
        m.setSucursalId(sucursalId);
        m.setCategoriaCodigo(cat);
        m.setObservaciones(trimNullable(observaciones, 4000));
        m.setCreatedByEmail(trimNullable(createdByEmail, 320));
        return movementRepository.save(m).getId();
    }

    private long findOrCreateItem(String sku, String name, String unit) {
        return itemRepository
                .findBySkuIgnoreCase(sku)
                .map(InvItem::getId)
                .orElseGet(
                        () -> {
                            InvItem i = new InvItem();
                            i.setSku(sku);
                            i.setName(name.length() > 512 ? name.substring(0, 512) : name);
                            i.setUnit(unit);
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

    private static String resolvePalePieceMaterial(PaleDetalle d) {
        if (d.getDescripcion() != null && !d.getDescripcion().isBlank()) {
            return d.getDescripcion().trim();
        }
        if (d.getPartCode() != null && !d.getPartCode().isBlank()) {
            return d.getPartCode().trim();
        }
        if (d.getOrderName() != null && !d.getOrderName().isBlank()) {
            return d.getOrderName().trim() + " #" + d.getPiezaId();
        }
        return "Pieza " + d.getPiezaId();
    }

    public static String normalizeCategoria(String raw) {
        if (raw == null || raw.isBlank()) {
            return CAT_DISPONIBLE;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case CAT_MERCA, CAT_REUTILIZABLE -> u;
            default -> CAT_DISPONIBLE;
        };
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
                m.getSucursalId(),
                m.getCategoriaCodigo(),
                m.getObservaciones(),
                m.getCreatedAt(),
                m.getCreatedByEmail());
    }
}
