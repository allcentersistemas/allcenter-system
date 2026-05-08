package com.allcenter.modulepale.repository;

import com.allcenter.modulepale.model.Pale;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaleRepository extends JpaRepository<Pale, Long> {
    Optional<Pale> findByCodigoIgnoreCase(String codigo);

    @Query(
            """
            SELECT p
            FROM Pale p
            LEFT JOIN FETCH p.sucursalOrigen
            LEFT JOIN FETCH p.sucursalDestino
            LEFT JOIN FETCH p.ubicacionOrigen
            LEFT JOIN FETCH p.ubicacionDestino
            """)
    List<Pale> findAllWithRelations();

    @Query(
            """
            SELECT p
            FROM Pale p
            LEFT JOIN FETCH p.sucursalOrigen
            LEFT JOIN FETCH p.sucursalDestino
            LEFT JOIN FETCH p.ubicacionOrigen
            LEFT JOIN FETCH p.ubicacionDestino
            WHERE p.id = :id
            """)
    Optional<Pale> findByIdWithRelations(@Param("id") Long id);

    @Query(
            """
            SELECT p
            FROM Pale p
            LEFT JOIN FETCH p.sucursalOrigen
            LEFT JOIN FETCH p.sucursalDestino
            LEFT JOIN FETCH p.ubicacionOrigen
            LEFT JOIN FETCH p.ubicacionDestino
            WHERE UPPER(p.codigo) = UPPER(:codigo)
            """)
    Optional<Pale> findByCodigoIgnoreCaseWithRelations(@Param("codigo") String codigo);

    @Query(
            value =
                    """
                    SELECT COALESCE(
                        MAX(
                            CASE
                                WHEN codigo ~ '^[0-9]{10}$' THEN CAST(codigo AS BIGINT)
                                ELSE NULL
                            END
                        ),
                        0
                    )
                    FROM pale
                    """,
            nativeQuery = true)
    Long findMaxNumericCode();
}
