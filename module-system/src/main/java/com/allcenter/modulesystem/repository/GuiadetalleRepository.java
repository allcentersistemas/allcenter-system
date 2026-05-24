package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.Guiadetalle;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuiadetalleRepository extends JpaRepository<Guiadetalle, Long> {

    List<Guiadetalle> findByGuiaIdOrderByIdAsc(Long guiaId);

    boolean existsByGuiaIdAndPaleId(Long guiaId, Long paleId);

    java.util.Optional<Guiadetalle> findFirstByPaleIdOrderByIdDesc(Long paleId);

    @org.springframework.data.jpa.repository.Query(
            """
            SELECT gd FROM Guiadetalle gd JOIN FETCH gd.guia
            WHERE gd.paleId = :paleId ORDER BY gd.id DESC
            """)
    java.util.List<Guiadetalle> findByPaleIdWithGuia(
            @org.springframework.data.repository.query.Param("paleId") Long paleId,
            org.springframework.data.domain.Pageable pageable);
}
