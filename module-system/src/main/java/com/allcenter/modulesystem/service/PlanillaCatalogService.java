package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.InventoryDtos;
import com.allcenter.modulesystem.dto.PlanillaCatalogDtos;
import com.allcenter.modulesystem.model.Canto;
import com.allcenter.modulesystem.model.Tablero;
import com.allcenter.modulesystem.repository.CantoRepository;
import com.allcenter.modulesystem.repository.TableroRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class PlanillaCatalogService {

    private final TableroRepository tableroRepository;
    private final CantoRepository cantoRepository;

    @Transactional(readOnly = true)
    public InventoryDtos.OptimizacionKardexCatalog listClientCatalog() {
        List<InventoryDtos.KardexMaterialOption> tableros =
                tableroRepository.findByActiveTrueOrderByNombreAsc().stream()
                        .map(this::toTableroOption)
                        .toList();
        List<InventoryDtos.KardexMaterialOption> cantos =
                cantoRepository.findByActiveTrueOrderByNombreAsc().stream()
                        .map(this::toCantoOption)
                        .toList();
        return new InventoryDtos.OptimizacionKardexCatalog(tableros, cantos);
    }

    @Transactional(readOnly = true)
    public Page<PlanillaCatalogDtos.TableroRow> pageTableros(String q, Pageable pageable) {
        Page<Tablero> page =
                q == null || q.isBlank()
                        ? tableroRepository.findByActiveTrueOrderByNombreAsc(pageable)
                        : tableroRepository.searchActive(q.trim(), pageable);
        return page.map(this::toTableroRow);
    }

    @Transactional(readOnly = true)
    public PlanillaCatalogDtos.TableroRow getTablero(long id) {
        return toTableroRow(requireTablero(id));
    }

    @Transactional
    public PlanillaCatalogDtos.TableroRow createTablero(PlanillaCatalogDtos.CreateTableroRequest req) {
        String codigo = requireCodigo(req.codigo());
        if (tableroRepository.findByCodigoIgnoreCase(codigo).isPresent()) {
            throw new ResponseStatusException(CONFLICT, "Ya existe un tablero con código " + codigo);
        }
        Tablero t = new Tablero();
        t.setCodigo(codigo);
        t.setNombre(requireNombre(req.nombre()));
        t.setEspesorMm(req.espesorMm());
        t.setUnidad(normalizeUnidad(req.unidad()));
        try {
            return toTableroRow(tableroRepository.save(t));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(CONFLICT, "Código de tablero duplicado");
        }
    }

    @Transactional
    public PlanillaCatalogDtos.TableroRow updateTablero(long id, PlanillaCatalogDtos.UpdateTableroRequest req) {
        Tablero t = requireTablero(id);
        if (req.codigo() != null) {
            String codigo = requireCodigo(req.codigo());
            tableroRepository
                    .findByCodigoIgnoreCase(codigo)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(
                            other -> {
                                throw new ResponseStatusException(CONFLICT, "Ya existe un tablero con código " + codigo);
                            });
            t.setCodigo(codigo);
        }
        if (req.nombre() != null) {
            t.setNombre(requireNombre(req.nombre()));
        }
        if (req.espesorMm() != null) {
            t.setEspesorMm(req.espesorMm());
        }
        if (req.unidad() != null) {
            t.setUnidad(normalizeUnidad(req.unidad()));
        }
        if (req.active() != null) {
            t.setActive(req.active());
        }
        return toTableroRow(tableroRepository.save(t));
    }

    @Transactional(readOnly = true)
    public Page<PlanillaCatalogDtos.CantoRow> pageCantos(String q, Pageable pageable) {
        Page<Canto> page =
                q == null || q.isBlank()
                        ? cantoRepository.findByActiveTrueOrderByNombreAsc(pageable)
                        : cantoRepository.searchActive(q.trim(), pageable);
        return page.map(this::toCantoRow);
    }

    @Transactional(readOnly = true)
    public PlanillaCatalogDtos.CantoRow getCanto(long id) {
        return toCantoRow(requireCanto(id));
    }

    @Transactional
    public PlanillaCatalogDtos.CantoRow createCanto(PlanillaCatalogDtos.CreateCantoRequest req) {
        String codigo = requireCodigo(req.codigo());
        if (cantoRepository.findByCodigoIgnoreCase(codigo).isPresent()) {
            throw new ResponseStatusException(CONFLICT, "Ya existe un canto con código " + codigo);
        }
        Canto c = new Canto();
        c.setCodigo(codigo);
        c.setNombre(requireNombre(req.nombre()));
        try {
            return toCantoRow(cantoRepository.save(c));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(CONFLICT, "Código de canto duplicado");
        }
    }

    @Transactional
    public PlanillaCatalogDtos.CantoRow updateCanto(long id, PlanillaCatalogDtos.UpdateCantoRequest req) {
        Canto c = requireCanto(id);
        if (req.codigo() != null) {
            String codigo = requireCodigo(req.codigo());
            cantoRepository
                    .findByCodigoIgnoreCase(codigo)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(
                            other -> {
                                throw new ResponseStatusException(CONFLICT, "Ya existe un canto con código " + codigo);
                            });
            c.setCodigo(codigo);
        }
        if (req.nombre() != null) {
            c.setNombre(requireNombre(req.nombre()));
        }
        if (req.active() != null) {
            c.setActive(req.active());
        }
        return toCantoRow(cantoRepository.save(c));
    }

    private Tablero requireTablero(long id) {
        return tableroRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tablero no encontrado"));
    }

    private Canto requireCanto(long id) {
        return cantoRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Canto no encontrado"));
    }

    private InventoryDtos.KardexMaterialOption toTableroOption(Tablero t) {
        return new InventoryDtos.KardexMaterialOption(
                t.getId(), t.getCodigo(), t.getNombre(), t.getUnidad(), BigDecimal.ZERO);
    }

    private InventoryDtos.KardexMaterialOption toCantoOption(Canto c) {
        return new InventoryDtos.KardexMaterialOption(c.getId(), c.getCodigo(), c.getNombre(), "UN", BigDecimal.ZERO);
    }

    private PlanillaCatalogDtos.TableroRow toTableroRow(Tablero t) {
        return new PlanillaCatalogDtos.TableroRow(
                t.getId(), t.getCodigo(), t.getNombre(), t.getEspesorMm(), t.getUnidad(), t.isActive(), t.getCreatedAt());
    }

    private PlanillaCatalogDtos.CantoRow toCantoRow(Canto c) {
        return new PlanillaCatalogDtos.CantoRow(c.getId(), c.getCodigo(), c.getNombre(), c.isActive(), c.getCreatedAt());
    }

    private static String requireCodigo(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "El código es obligatorio");
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String requireNombre(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "El nombre es obligatorio");
        }
        return raw.trim();
    }

    private static String normalizeUnidad(String raw) {
        if (raw == null || raw.isBlank()) {
            return "PLN";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}
