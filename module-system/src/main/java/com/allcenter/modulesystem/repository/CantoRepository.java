package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.Canto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CantoRepository extends JpaRepository<Canto, Long> {

    Optional<Canto> findByCodigoIgnoreCase(String codigo);

    List<Canto> findByActiveTrueOrderByNombreAsc();

    Page<Canto> findByActiveTrueOrderByNombreAsc(Pageable pageable);

    @Query(
            """
            select c from Canto c
            where c.active = true
            and (
              lower(c.codigo) like lower(concat('%', :q, '%'))
              or lower(c.nombre) like lower(concat('%', :q, '%'))
            )
            order by c.nombre asc
            """)
    Page<Canto> searchActive(@Param("q") String q, Pageable pageable);
}
