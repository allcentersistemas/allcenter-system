package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.Tablero;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TableroRepository extends JpaRepository<Tablero, Long> {

    Optional<Tablero> findByCodigoIgnoreCase(String codigo);

    List<Tablero> findByActiveTrueOrderByNombreAsc();

    Page<Tablero> findByActiveTrueOrderByNombreAsc(Pageable pageable);

    @Query(
            """
            select t from Tablero t
            where t.active = true
            and (
              lower(t.codigo) like lower(concat('%', :q, '%'))
              or lower(t.nombre) like lower(concat('%', :q, '%'))
            )
            order by t.nombre asc
            """)
    Page<Tablero> searchActive(@Param("q") String q, Pageable pageable);
}
