package com.allcenter.modulebiesse.repository;

import com.allcenter.modulebiesse.model.ImpresionSticker;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImpresionStickerRepository extends JpaRepository<ImpresionSticker, Long> {

    @Query(
            """
            SELECT i FROM ImpresionSticker i
            WHERE (:orderId IS NULL OR i.orderId = :orderId)
              AND (:from IS NULL OR i.fecha >= :from)
              AND (:to IS NULL OR i.fecha <= :to)
            ORDER BY i.fecha DESC
            """)
    List<ImpresionSticker> search(
            @Param("orderId") Long orderId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);
}
