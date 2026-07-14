package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.AuditEntry;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    @Query(
            """
            SELECT a FROM AuditEntry a
            WHERE (:entityId IS NULL OR a.entityId = :entityId)
              AND (:entityTypes IS NULL OR a.entityType IN :entityTypes)
            """)
    Page<AuditEntry> findFiltered(
            @Param("entityId") String entityId,
            @Param("entityTypes") List<String> entityTypes,
            Pageable pageable);

    @Query(
            """
            SELECT a FROM AuditEntry a
            WHERE a.actorClientUserId = :clientUserId
              AND a.action IN :actions
            ORDER BY a.occurredAt DESC
            """)
    Page<AuditEntry> findClientAuthHistory(
            @Param("clientUserId") Long clientUserId,
            @Param("actions") List<com.allcenter.modulesystem.model.AuditAction> actions,
            Pageable pageable);
}
