package com.allcenter.moduleemployee.repository;

import com.allcenter.moduleemployee.model.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHashAndRevokedIsFalse(String tokenHash);

    @Modifying
    @Query(
            "UPDATE RefreshToken r SET r.revoked = true WHERE r.employeeId = :employeeId AND r.revoked = false")
    int revokeAllActiveForEmployee(@Param("employeeId") Long employeeId);
}
