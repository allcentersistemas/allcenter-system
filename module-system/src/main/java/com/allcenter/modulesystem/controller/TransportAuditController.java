package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.TransportDtos.TransportAuditEntryDto;
import com.allcenter.modulesystem.model.TransportAuditEntry;
import com.allcenter.modulesystem.repository.TransportAuditEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transport")
@RequiredArgsConstructor
public class TransportAuditController {

    private final TransportAuditEntryRepository auditEntryRepository;

    /**
     * Consulta paginada de auditoría. Filtros opcionales (combinables).
     *
     * <p>{@code correlationId}: usar el ID de carga para ver toda la trazabilidad de una expedición (vehículo,
     * cambios de estado, pales agregados o quitados).
     */
    @GetMapping("/auditoria")
    @PreAuthorize("@portalAuth.canAudit()")
    public ResponseEntity<Page<TransportAuditEntryDto>> listAuditoria(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String correlationId,
            @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        String et = blankToNull(entityType);
        String eid = blankToNull(entityId);
        String corr = blankToNull(correlationId);
        Page<TransportAuditEntry> page = auditEntryRepository.search(et, eid, corr, pageable);
        return ResponseEntity.ok(page.map(this::toDto));
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private TransportAuditEntryDto toDto(TransportAuditEntry e) {
        return new TransportAuditEntryDto(
                e.getId(),
                e.getOccurredAt(),
                e.getAction().name(),
                e.getEntityType(),
                e.getEntityId(),
                e.getCorrelationId(),
                e.getActorEmployeeId(),
                e.getActorEmail(),
                e.getClientIpPublic(),
                e.getDetails());
    }
}
