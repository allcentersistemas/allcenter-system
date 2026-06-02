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
              upper(i.familiaCodigo) = upper(:familia)
              or (
                i.familiaCodigo is null
                and (
                  (:familia = 'TABLERO' and (lower(i.sku) like 'tab%' or lower(i.sku) like 'tbl%'))
                  or (:familia = 'CANTO' and lower(i.sku) like 'cant%')
                )
              )
            )
            order by i.name asc
            """)
    java.util.List<InvItem> findActiveCatalogByFamilia(@Param("familia") String familia, Pageable pageable);
}
