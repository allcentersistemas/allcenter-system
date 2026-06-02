package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.InvItem;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvItemRepository extends JpaRepository<InvItem, Long> {

    Optional<InvItem> findBySkuIgnoreCase(String sku);

    Page<InvItem> findByActiveTrue(Pageable pageable);

    @Query(
            """
            select i from InvItem i
            where i.active = true
            and (
              lower(i.sku) like lower(concat('%', :q, '%'))
              or lower(i.name) like lower(concat('%', :q, '%'))
            )
            """)
    Page<InvItem> searchActive(@Param("q") String q, Pageable pageable);

    @Query(
            """
            select i from InvItem i
            where i.active = true
            and (
              :q = '' or lower(i.sku) like lower(concat('%', :q, '%'))
              or lower(i.name) like lower(concat('%', :q, '%'))
            )
            and (
              :sucursalId is null
              or i.id in (
                select m.item.id from InvStockMovement m
                where m.sucursalId = :sucursalId
                  and (m.categoriaCodigo = 'DISPONIBLE' or m.categoriaCodigo is null)
                group by m.item.id
                having sum(m.quantityChange) <> 0
              )
            )
            and (
              :tipo = '' or
              (:tipo = 'PALET' and upper(i.sku) like 'PALET-%') or
              (:tipo = 'PIEZA' and upper(i.sku) like 'RM-%') or
              (:tipo = 'OTROS' and upper(i.sku) not like 'PALET-%' and upper(i.sku) not like 'RM-%')
            )
            """)
    Page<InvItem> searchActiveFiltered(
            @Param("q") String q,
            @Param("sucursalId") Long sucursalId,
            @Param("tipo") String tipo,
            Pageable pageable);
}
