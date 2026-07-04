package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.exception.NotFoundException;
import com.allcenter.modulesystem.dto.AuditEntryResponse;
import com.allcenter.modulesystem.repository.AuditEntryRepository;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditEntryRepository auditEntryRepository;

    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> findAll(Pageable pageable) {
        return auditEntryRepository.findAll(pageable).map(AuditEntryResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> findFiltered(String entityTypeCsv, String entityId, Pageable pageable) {
        String trimmedId = trimToNull(entityId);
        List<String> types = parseEntityTypes(entityTypeCsv);
        if (trimmedId == null && types == null) {
            return findAll(pageable);
        }
        return auditEntryRepository
                .findFiltered(trimmedId, types, pageable)
                .map(AuditEntryResponse::from);
    }

    @Transactional(readOnly = true)
    public AuditEntryResponse getById(Long id) {
        return auditEntryRepository
                .findById(id)
                .map(AuditEntryResponse::from)
                .orElseThrow(() -> new NotFoundException("No existe un registro de auditoría con id " + id));
    }

    private static List<String> parseEntityTypes(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }
        List<String> types =
                Arrays.stream(csv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .distinct()
                        .toList();
        return types.isEmpty() ? null : types;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
