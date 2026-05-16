package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.TransportAuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransportAuditEntryRepository extends JpaRepository<TransportAuditEntry, Long> {

    @Query(
            """
            SELECT e FROM TransportAuditEntry e
            WHERE (:entityType IS NULL OR e.entityType = :entityType)
            AND (:entityId IS NULL OR e.entityId = :entityId)
            AND (:correlationId IS NULL OR e.correlationId = :correlationId)
            """)
    Page<TransportAuditEntry> search(
            @Param("entityType") String entityType,
            @Param("entityId") String entityId,
            @Param("correlationId") String correlationId,
            Pageable pageable);
}
