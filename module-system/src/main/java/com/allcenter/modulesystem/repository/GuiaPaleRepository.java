package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.GuiaPale;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuiaPaleRepository extends JpaRepository<GuiaPale, Long> {

    boolean existsByGuiaIdAndPaleId(Long guiaId, Long paleId);

    boolean existsByCodigoIgnoreCase(String codigo);

    long countByGuiaId(Long guiaId);

    @Query(
            "SELECT gp FROM GuiaPale gp JOIN FETCH gp.pale JOIN FETCH gp.guia WHERE gp.guia.id = :guiaId ORDER BY gp.fechaRegistro DESC")
    List<GuiaPale> findByGuiaIdWithPale(@Param("guiaId") Long guiaId);

    @Query("SELECT gp FROM GuiaPale gp JOIN FETCH gp.pale JOIN FETCH gp.guia WHERE gp.id = :id")
    Optional<GuiaPale> findByIdWithRelations(@Param("id") Long id);

    Optional<GuiaPale> findByCodigoIgnoreCase(String codigo);
}
