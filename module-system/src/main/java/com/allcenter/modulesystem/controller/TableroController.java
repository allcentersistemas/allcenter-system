package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.PlanillaCatalogDtos;
import com.allcenter.modulesystem.service.PlanillaCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/tableros")
@RequiredArgsConstructor
public class TableroController {

    private final PlanillaCatalogService catalogService;

    @GetMapping
    @PreAuthorize("@portalAuth.canRead()")
    public Page<PlanillaCatalogDtos.TableroRow> list(
            @RequestParam(required = false) String q, @PageableDefault(size = 20) Pageable pageable) {
        return catalogService.pageTableros(q, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@portalAuth.canRead()")
    public PlanillaCatalogDtos.TableroRow get(@PathVariable long id) {
        return catalogService.getTablero(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@portalAuth.canCreate()")
    public PlanillaCatalogDtos.TableroRow create(@Valid @RequestBody PlanillaCatalogDtos.CreateTableroRequest body) {
        return catalogService.createTablero(body);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@portalAuth.canUpdate()")
    public PlanillaCatalogDtos.TableroRow update(
            @PathVariable long id, @RequestBody PlanillaCatalogDtos.UpdateTableroRequest body) {
        return catalogService.updateTablero(id, body);
    }
}
