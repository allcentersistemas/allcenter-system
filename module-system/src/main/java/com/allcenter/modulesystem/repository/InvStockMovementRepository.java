package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.InvStockMovement;
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

    @Query(
            """
            select coalesce(sum(m.quantityChange), 0) from InvStockMovement m
            where m.item.id = :itemId
              and (:sucursalId is null and m.sucursalId is null or m.sucursalId = :sucursalId)
              and (:categoria is null or m.categoriaCodigo = :categoria
                   or (:categoria = 'DISPONIBLE' and m.categoriaCodigo is null))
            """)
    BigDecimal sumQuantityChangeByItemIdSucursalCategoria(
            @Param("itemId") long itemId,
            @Param("sucursalId") Long sucursalId,
            @Param("categoria") String categoria);

    boolean existsByExternalRef(String externalRef);
}
