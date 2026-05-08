package com.allcenter.moduleemployee.repository;

import com.allcenter.moduleemployee.model.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {}
