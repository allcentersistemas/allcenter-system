package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.InventoryDtos;
import com.allcenter.modulesystem.service.InventoryApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryApplicationService inventoryService;

    @GetMapping("/items")
    public Page<InventoryDtos.ItemRow> listItems(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return inventoryService.pageItems(q, pageable);
    }

    @GetMapping("/items/{id}")
    public InventoryDtos.ItemDetail getItem(@PathVariable long id) {
        return inventoryService.getItemDetail(id);
    }

    @PostMapping("/items")
    public ResponseEntity<InventoryDtos.Created> createItem(
            @Valid @RequestBody InventoryDtos.CreateItemRequest body, HttpServletRequest request) {
        long id = inventoryService.createItem(body, trimHeaderEmail(request));
        return ResponseEntity.ok(new InventoryDtos.Created(id));
    }

    @PostMapping("/items/{id}/movements")
    public ResponseEntity<InventoryDtos.Created> addMovement(
            @PathVariable long id,
            @Valid @RequestBody InventoryDtos.CreateMovementRequest body,
            HttpServletRequest request) {
        long mid = inventoryService.addMovement(id, body, trimHeaderEmail(request));
        return ResponseEntity.ok(new InventoryDtos.Created(mid));
    }

    private static String trimHeaderEmail(HttpServletRequest request) {
        String h = request.getHeader("X-User-Email");
        if (h == null) {
            return null;
        }
        String t = h.trim();
        return t.isEmpty() ? null : t.substring(0, Math.min(320, t.length()));
    }
}
