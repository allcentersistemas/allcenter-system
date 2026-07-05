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
              AND (:tipoRegistro IS NULL OR LOWER(CAST(v.tiporegistro AS string)) = LOWER(CAST(:tipoRegistro AS string)))
              AND (
                :q IS NULL OR (
                    CONCAT('', v.numeroregistro, '') LIKE CONCAT('%', :q, '%') OR
                    LOWER(CAST(COALESCE(v.placa, '') AS string)) LIKE CONCAT('%', :q, '%') OR
                    LOWER(CAST(COALESCE(v.chofer, '') AS string)) LIKE CONCAT('%', :q, '%') OR
                    LOWER(CAST(COALESCE(v.marca, '') AS string)) LIKE CONCAT('%', :q, '%') OR
                    LOWER(CAST(COALESCE(v.tiporegistro, '') AS string)) LIKE CONCAT('%', :q, '%') OR
                    EXISTS (
                        SELECT 1 FROM RmRegistroEntrada e WHERE e.registroVehiculo = v AND (
                            LOWER(CAST(COALESCE(e.numeroGuia, '') AS string)) LIKE CONCAT('%', :q, '%') OR
                            LOWER(CAST(COALESCE(e.ocNumero, '') AS string)) LIKE CONCAT('%', :q, '%')
                        )
                    ) OR
                    EXISTS (
                        SELECT 1 FROM RmRegistroSalida s WHERE s.registroVehiculo = v AND (
                            LOWER(CAST(COALESCE(s.numeroGuia, '') AS string)) LIKE CONCAT('%', :q, '%') OR
                            LOWER(CAST(COALESCE(s.ocNumero, '') AS string)) LIKE CONCAT('%', :q, '%')
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
