package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.exception.NotFoundException;
import com.allcenter.modulesystem.dto.AuditEntryResponse;
import com.allcenter.modulesystem.repository.AuditEntryRepository;
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
    public AuditEntryResponse getById(Long id) {
        return auditEntryRepository
                .findById(id)
                .map(AuditEntryResponse::from)
                .orElseThrow(() -> new NotFoundException("No existe un registro de auditoría con id " + id));
    }
}
