package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.AuditEntryResponse;
import com.allcenter.modulesystem.service.AuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditQueryService auditQueryService;

    @GetMapping("/entries")
    @PreAuthorize("@portalAuth.canGestion()")
    public ResponseEntity<Page<AuditEntryResponse>> list(
            @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(auditQueryService.findAll(pageable));
    }

    @GetMapping("/entries/{id}")
    @PreAuthorize("@portalAuth.canGestion()")
    public ResponseEntity<AuditEntryResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(auditQueryService.getById(id));
    }
}
