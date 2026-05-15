package com.allcenter.moduleinventory.repository;

import com.allcenter.moduleinventory.model.InvStockMovement;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvStockMovementRepository extends JpaRepository<InvStockMovement, Long> {

    Page<InvStockMovement> findByItem_IdOrderByCreatedAtDesc(long itemId, Pageable pageable);

    @Query("select coalesce(sum(m.quantityChange), 0) from InvStockMovement m where m.item.id = :itemId")
    BigDecimal sumQuantityChangeByItemId(@Param("itemId") long itemId);
}
