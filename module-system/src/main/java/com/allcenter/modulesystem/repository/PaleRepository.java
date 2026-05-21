package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.Pale;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaleRepository extends JpaRepository<Pale, Long> {
    Optional<Pale> findByCodigoIgnoreCase(String codigo);


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
