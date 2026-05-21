package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.Guiadetalle;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuiadetalleRepository extends JpaRepository<Guiadetalle, Long> {

    List<Guiadetalle> findByGuiaIdOrderByIdAsc(Long guiaId);

    boolean existsByGuiaIdAndPaleId(Long guiaId, Long paleId);
}
