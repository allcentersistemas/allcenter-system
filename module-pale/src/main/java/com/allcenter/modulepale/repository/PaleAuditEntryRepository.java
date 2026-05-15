package com.allcenter.modulepale.repository;

import com.allcenter.modulepale.model.PaleAuditEntry;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaleAuditEntryRepository extends JpaRepository<PaleAuditEntry, Long> {

    List<PaleAuditEntry> findByPaleIdOrderByOccurredAtDesc(Long paleId, Pageable pageable);

    List<PaleAuditEntry> findByActionIgnoreCaseOrderByOccurredAtDesc(String action, Pageable pageable);

    List<PaleAuditEntry> findByPaleIdAndActionIgnoreCaseOrderByOccurredAtDesc(
            Long paleId, String action, Pageable pageable);

    List<PaleAuditEntry> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
