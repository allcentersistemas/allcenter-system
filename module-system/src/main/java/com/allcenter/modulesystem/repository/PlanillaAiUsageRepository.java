package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.PlanillaAiUsage;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanillaAiUsageRepository extends JpaRepository<PlanillaAiUsage, Long> {

    long countByClientUserIdAndCreatedAtGreaterThanEqual(Long clientUserId, Instant since);

    Page<PlanillaAiUsage> findByClientUserIdOrderByCreatedAtDesc(Long clientUserId, Pageable pageable);

    @Query(
            """
            SELECT COUNT(u),
                   COALESCE(SUM(CASE WHEN u.success = true THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN u.success = false THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(u.inputTokens), 0),
                   COALESCE(SUM(u.outputTokens), 0)
            FROM PlanillaAiUsage u
            WHERE u.clientUserId = :clientUserId
            """)
    List<Object[]> summarizeByClient(@Param("clientUserId") Long clientUserId);

    @Query(
            """
            SELECT COUNT(u),
                   COALESCE(SUM(CASE WHEN u.success = true THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN u.success = false THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(u.inputTokens), 0),
                   COALESCE(SUM(u.outputTokens), 0)
            FROM PlanillaAiUsage u
            WHERE u.createdAt >= :since
            """)
    List<Object[]> summarizeSince(@Param("since") Instant since);

    @Query(
            """
            SELECT COUNT(u),
                   COALESCE(SUM(CASE WHEN u.success = true THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN u.success = false THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(u.inputTokens), 0),
                   COALESCE(SUM(u.outputTokens), 0)
            FROM PlanillaAiUsage u
            """)
    List<Object[]> summarizeAll();

    @Query(
            """
            SELECT u.clientUserId,
                   COALESCE(SUM(COALESCE(u.inputTokens, 0) + COALESCE(u.outputTokens, 0)), 0),
                   COALESCE(SUM(COALESCE(u.inputTokens, 0)), 0),
                   COALESCE(SUM(COALESCE(u.outputTokens, 0)), 0),
                   COUNT(u)
            FROM PlanillaAiUsage u
            GROUP BY u.clientUserId
            ORDER BY COALESCE(SUM(COALESCE(u.inputTokens, 0) + COALESCE(u.outputTokens, 0)), 0) DESC
            """)
    List<Object[]> topConsumersByTokens(Pageable pageable);
}
