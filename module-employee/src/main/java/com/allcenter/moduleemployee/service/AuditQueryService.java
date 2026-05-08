package com.allcenter.moduleemployee.service;

import com.allcenter.moduleemployee.exception.NotFoundException;
import com.allcenter.moduleemployee.model.dto.AuditEntryResponse;
import com.allcenter.moduleemployee.repository.AuditEntryRepository;
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
