package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {}
