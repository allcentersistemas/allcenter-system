package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.InventoryDtos;
import com.allcenter.modulesystem.model.Guia;
import com.allcenter.modulesystem.model.Guiadetalle;
import com.allcenter.modulesystem.model.InvItem;
import com.allcenter.modulesystem.model.InvStockMovement;
import com.allcenter.modulesystem.model.Pale;
import com.allcenter.modulesystem.model.PaleDetalle;
import com.allcenter.modulesystem.dto.RmPayloadModels;
import com.allcenter.modulesystem.repository.GuiaRepository;
import com.allcenter.modulesystem.repository.InvItemRepository;
import com.allcenter.modulesystem.repository.InvStockMovementRepository;
import com.allcenter.modulesystem.repository.PaleDetalleRepository;
import com.allcenter.modulesystem.repository.PaleRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final GuiaRepository guiaRepository;
    private final PaleRepository paleRepository;
    private final PaleDetalleRepository paleDetalleRepository;
    private final AppConfigService appConfigService;

    public List<InventoryDtos.CategoriaRow> listCategorias() {
        return List.of(
                new InventoryDtos.CategoriaRow(CAT_DISPONIBLE, "Disponible"),
                new InventoryDtos.CategoriaRow(CAT_MERCA, "Merma / merca"),
                new InventoryDtos.CategoriaRow(CAT_REUTILIZABLE, "Reutilizable"));
    }

    @Transactional(readOnly = true)
    public Page<InventoryDtos.ItemRow> pageItems(
            String q, Long sucursalId, String tipo, Pageable pageable) {
        String qNorm = q == null || q.isBlank() ? "" : q.trim();
        String tipoNorm = normalizeTipoFilter(tipo);
        Pageable sorted =
                PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(Sort.Order.asc("sku")));
        Page<InvItem> page = itemRepository.searchActiveFiltered(qNorm, sucursalId, tipoNorm, sorted);
        return page.map(item -> toRowWithBalance(item, sucursalId));
    }

    private static String normalizeTipoFilter(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return "";
        }
        return switch (tipo.trim().toUpperCase(Locale.ROOT)) {
            case "PALET", "PIEZA", "OTROS" -> tipo.trim().toUpperCase(Locale.ROOT);
            default -> "";
        };
    }

    private InventoryDtos.ItemRow toRowWithBalance(InvItem item, Long sucursalId) {
        InventoryDtos.ItemRow row = toRow(item);
        if (sucursalId == null) {
            return row;
        }
        BigDecimal balance =
                movementRepository.sumQuantityChangeByItemIdSucursalCategoria(
                        item.getId(), sucursalId, CAT_DISPONIBLE);
        return new InventoryDtos.ItemRow(
                row.id(),
                row.sku(),
                row.name(),
                row.unit(),
                row.active(),
                row.familiaCodigo(),
                row.tipoInventario(),
                balance,
                row.createdAt());
    }

    @Transactional
    public long createItem(InventoryDtos.CreateItemRequest req, String createdByEmail) {
        throw new ResponseStatusException(
                BAD_REQUEST,
                "La creación manual de artículos está deshabilitada. El inventario se genera desde palés y piezas.");
    }

    @Transactional(readOnly = true)
    public InventoryDtos.ItemDetail getItemDetail(long id, Long sucursalId) {
        InvItem item =
                itemRepository.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Artículo no encontrado"));
        BigDecimal balance =
                sucursalId != null
                        ? movementRepository.sumQuantityChangeByItemIdSucursalCategoria(
                                id, sucursalId, CAT_DISPONIBLE)
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
        throw new ResponseStatusException(
                BAD_REQUEST,
                "Los movimientos manuales están deshabilitados. El kardex se actualiza al crear/cerrar palés y al despachar guías con palés.");
    }

    /**
     * Registra el palé en el almacén de la sucursal (unidad logística).
     */
    @Transactional
    public void registerPaleInWarehouse(Pale pale, Long sucursalId, String createdByEmail) {
        if (!appConfigService.isKardexEnabled()) {
            return;
        }
        if (sucursalId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "El empleado debe tener sucursal asignada para registrar el palé en almacén");
        }
        String ref = "pale_open:" + pale.getId();
        if (movementRepository.existsByExternalRef(ref)) {
            return;
        }
        String sku = "PALET-" + pale.getCodigo();
        long itemId = findOrCreateItem(sku, "Palé " + pale.getCodigo(), UNIT_PIEZAS, "PALET");
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
        if (!appConfigService.isKardexEnabled()) {
            return;
        }
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
            long itemId = findOrCreateItem(toSku(material), material, UNIT_PIEZAS, "PIEZA");
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
        if (!appConfigService.isKardexEnabled()) {
            return;
        }
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
        if (!appConfigService.isKardexEnabled()) {
            return;
        }
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

    /**
     * Kardex solo por palés: al validar salida con guía, descuenta SKU del palé y cada pieza escaneada.
     * No hay movimiento de inventario por líneas manuales (RM ni guía manual).
     */
    @Transactional(readOnly = true)
    public List<StockLine> buildStockLinesForRmSalida(
            Long guiaInventarioId, List<RmPayloadModels.SalidaDetalle> salidaDetalles) {
        if (guiaInventarioId == null || salidaDetalles == null || salidaDetalles.isEmpty()) {
            return List.of();
        }
        Guia guia =
                guiaRepository
                        .findByIdWithDetalles(guiaInventarioId)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Guía de inventario no encontrada"));
        List<StockLine> out = new ArrayList<>();
        for (RmPayloadModels.SalidaDetalle sd : salidaDetalles) {
            Guiadetalle gd = findMatchingGuiaDetalle(guia, sd).orElse(null);
            if (gd != null && gd.getPaleId() != null) {
                expandPaleSalidaStockLines(gd.getPaleId(), sd.categoriaCodigo(), sd.observaciones(), out);
            }
        }
        return out;
    }

    private void expandPaleSalidaStockLines(
            Long paleId, String categoriaCodigo, String observaciones, List<StockLine> out) {
        Pale pale =
                paleRepository
                        .findById(paleId)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Palé no encontrado para salida"));
        String paleSku = "PALET-" + pale.getCodigo().trim().toUpperCase(Locale.ROOT);
        out.add(new StockLine(paleSku, "1", CAT_DISPONIBLE, observaciones));
        List<PaleDetalle> piezas = paleDetalleRepository.findByPale_IdOrderByFechaAgregadoDesc(paleId);
        for (PaleDetalle d : piezas) {
            String material = resolvePalePieceMaterial(d);
            if (material.isEmpty()) {
                continue;
            }
            out.add(new StockLine(material, "1", categoriaCodigo, observaciones));
        }
    }

    private java.util.Optional<Guiadetalle> findMatchingGuiaDetalle(
            Guia guia, RmPayloadModels.SalidaDetalle sd) {
        String salidaMat = normalizeSalidaMaterialKey(sd.materialProducto());
        if (salidaMat.isEmpty()) {
            return java.util.Optional.empty();
        }
        for (Guiadetalle gd : guia.getDetalles()) {
            if (salidaMat.equals(normalizeSalidaMaterialKey(buildGuiaSalidaMaterialLabel(gd)))) {
                return java.util.Optional.of(gd);
            }
            if (gd.getPaleId() != null) {
                String codigo =
                        paleRepository
                                .findById(gd.getPaleId())
                                .map(p -> p.getCodigo() == null ? "" : p.getCodigo().trim().toLowerCase(Locale.ROOT))
                                .orElse("");
                if (!codigo.isEmpty() && salidaMat.contains(codigo)) {
                    return java.util.Optional.of(gd);
                }
            }
        }
        return java.util.Optional.empty();
    }

    /** Etiqueta como la app Android al cargar líneas desde guía. */
    private String buildGuiaSalidaMaterialLabel(Guiadetalle gd) {
        String desc = gd.getDescripcion() == null ? "" : gd.getDescripcion().trim();
        if (gd.getPaleId() == null) {
            return desc;
        }
        String codigo =
                paleRepository
                        .findById(gd.getPaleId())
                        .map(p -> p.getCodigo() == null ? "" : p.getCodigo().trim())
                        .orElse(String.valueOf(gd.getPaleId()));
        if (codigo.isEmpty()) {
            return desc;
        }
        return ("Palé " + codigo + ": " + desc).trim();
    }

    private static String normalizeSalidaMaterialKey(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
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
        String sku = resolveItemSku(material);
        long itemId = findOrCreateItem(sku, material, UNIT_PIEZAS, inferFamiliaFromSku(sku));
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
                BigDecimal solicitado = delta.abs();
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "Stock insuficiente en sucursal "
                                + sucursalId
                                + " para "
                                + item.getSku()
                                + " (categoría "
                                + cat
                                + "): disponible "
                                + onHand
                                + ", solicitado "
                                + solicitado);
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

    private long findOrCreateItem(String sku, String name, String unit, String familiaCodigo) {
        return itemRepository
                .findBySkuIgnoreCase(sku)
                .map(
                        existing -> {
                            if (familiaCodigo != null
                                    && (existing.getFamiliaCodigo() == null
                                            || existing.getFamiliaCodigo().isBlank())) {
                                existing.setFamiliaCodigo(familiaCodigo);
                                itemRepository.save(existing);
                            }
                            return existing.getId();
                        })
                .orElseGet(
                        () -> {
                            InvItem i = new InvItem();
                            i.setSku(sku);
                            i.setName(name.length() > 512 ? name.substring(0, 512) : name);
                            i.setUnit(unit);
                            i.setFamiliaCodigo(familiaCodigo);
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

    /** Compatibilidad interna sin familia explícita. */
    private long findOrCreateItem(String sku, String name, String unit) {
        return findOrCreateItem(sku, name, unit, inferFamiliaFromSku(sku));
    }

    private static String inferFamiliaFromSku(String sku) {
        if (sku == null) {
            return null;
        }
        String u = sku.trim().toUpperCase(Locale.ROOT);
        if (u.startsWith("PALET-")) {
            return "PALET";
        }
        if (u.startsWith("RM-")) {
            return "PIEZA";
        }
        return null;
    }

    static String inferTipoInventario(InvItem item) {
        if (item.getFamiliaCodigo() != null && !item.getFamiliaCodigo().isBlank()) {
            String f = item.getFamiliaCodigo().trim().toUpperCase(Locale.ROOT);
            if ("PALET".equals(f) || "PIEZA".equals(f) || "TABLERO".equals(f) || "CANTO".equals(f)) {
                return f;
            }
        }
        String sku = item.getSku() == null ? "" : item.getSku().trim().toUpperCase(Locale.ROOT);
        if (sku.startsWith("PALET-")) {
            return "PALET";
        }
        if (sku.startsWith("RM-")) {
            return "PIEZA";
        }
        return "OTROS";
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

    private static String resolveItemSku(String material) {
        String m = material.trim();
        if (m.length() >= 6 && m.regionMatches(true, 0, "PALET-", 0, 6)) {
            return m.toUpperCase(Locale.ROOT);
        }
        return toSku(material);
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
        return new InventoryDtos.ItemRow(
                i.getId(),
                i.getSku(),
                i.getName(),
                i.getUnit(),
                i.isActive(),
                i.getFamiliaCodigo(),
                inferTipoInventario(i),
                i.getCreatedAt());
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
