package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.RmRegistroVehiculo;
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
            WHERE (
                LOWER(CAST(v.numeroregistro AS string)) LIKE CONCAT('%', :q, '%') OR
                LOWER(COALESCE(v.placa, '')) LIKE CONCAT('%', :q, '%') OR
                LOWER(COALESCE(v.chofer, '')) LIKE CONCAT('%', :q, '%') OR
                LOWER(COALESCE(v.marca, '')) LIKE CONCAT('%', :q, '%') OR
                LOWER(COALESCE(v.tiporegistro, '')) LIKE CONCAT('%', :q, '%') OR
                LOWER(COALESCE(v.guiaNumero, '')) LIKE CONCAT('%', :q, '%') OR
                LOWER(COALESCE(v.ocNumero, '')) LIKE CONCAT('%', :q, '%') OR
                EXISTS (
                    SELECT 1 FROM RmRegistroEntrada e WHERE e.registroVehiculo = v AND (
                        LOWER(COALESCE(e.numeroGuia, '')) LIKE CONCAT('%', :q, '%') OR
                        LOWER(COALESCE(e.ocNumero, '')) LIKE CONCAT('%', :q, '%') OR
                        EXISTS (
                            SELECT 1 FROM Guia g WHERE g.id = e.guiaInventarioId AND (
                                LOWER(g.numeroGuia) LIKE CONCAT('%', :q, '%') OR
                                LOWER(COALESCE(g.ordenCompra, '')) LIKE CONCAT('%', :q, '%')
                            )
                        )
                    )
                ) OR
                EXISTS (
                    SELECT 1 FROM RmRegistroSalida s WHERE s.registroVehiculo = v AND (
                        LOWER(COALESCE(s.numeroGuia, '')) LIKE CONCAT('%', :q, '%') OR
                        LOWER(COALESCE(s.ordenCompra, '')) LIKE CONCAT('%', :q, '%') OR
                        EXISTS (
                            SELECT 1 FROM Guia g2 WHERE g2.id = s.guiaInventarioId AND (
                                LOWER(g2.numeroGuia) LIKE CONCAT('%', :q, '%') OR
                                LOWER(COALESCE(g2.ordenCompra, '')) LIKE CONCAT('%', :q, '%')
                            )
                        )
                    )
                )
            )
            ORDER BY v.createdAt DESC
            """)
    Page<RmRegistroVehiculo> searchByTerm(@Param("q") String q, Pageable pageable);

    @Query("select coalesce(max(v.numeroregistro), 0) from RmRegistroVehiculo v")
    int findMaxNumeroRegistro();
}
