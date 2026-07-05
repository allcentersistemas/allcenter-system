package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.RmRegistroVehiculo;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RmRegistroVehiculoRepository extends JpaRepository<RmRegistroVehiculo, Long> {

    Page<RmRegistroVehiculo> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query(
            """
            SELECT DISTINCT v FROM RmRegistroVehiculo v
            WHERE (:fechaDesde IS NULL OR v.fecha >= :fechaDesde)
              AND (:fechaHasta IS NULL OR v.fecha <= :fechaHasta)
              AND (:tipoRegistro IS NULL OR LOWER(v.tiporegistro) = LOWER(:tipoRegistro))
              AND (
                :q IS NULL OR (
                    LOWER(CAST(v.numeroregistro AS string)) LIKE CONCAT('%', :q, '%') OR
                    LOWER(COALESCE(v.placa, '')) LIKE CONCAT('%', :q, '%') OR
                    LOWER(COALESCE(v.chofer, '')) LIKE CONCAT('%', :q, '%') OR
                    LOWER(COALESCE(v.marca, '')) LIKE CONCAT('%', :q, '%') OR
                    LOWER(COALESCE(v.tiporegistro, '')) LIKE CONCAT('%', :q, '%') OR
                    EXISTS (
                        SELECT 1 FROM RmRegistroEntrada e WHERE e.registroVehiculo = v AND (
                            LOWER(COALESCE(e.numeroGuia, '')) LIKE CONCAT('%', :q, '%') OR
                            LOWER(COALESCE(e.ocNumero, '')) LIKE CONCAT('%', :q, '%')
                        )
                    ) OR
                    EXISTS (
                        SELECT 1 FROM RmRegistroSalida s WHERE s.registroVehiculo = v AND (
                            LOWER(COALESCE(s.numeroGuia, '')) LIKE CONCAT('%', :q, '%') OR
                            LOWER(COALESCE(s.ocNumero, '')) LIKE CONCAT('%', :q, '%')
                        )
                    )
                )
              )
            ORDER BY v.createdAt DESC
            """)
    Page<RmRegistroVehiculo> pageFiltered(
            @Param("q") String q,
            @Param("fechaDesde") LocalDate fechaDesde,
            @Param("fechaHasta") LocalDate fechaHasta,
            @Param("tipoRegistro") String tipoRegistro,
            Pageable pageable);

    @Query("select coalesce(max(v.numeroregistro), 0) from RmRegistroVehiculo v")
    int findMaxNumeroRegistro();
}
