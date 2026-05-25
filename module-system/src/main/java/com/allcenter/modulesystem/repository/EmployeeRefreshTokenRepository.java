package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.EmployeeRefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRefreshTokenRepository extends JpaRepository<EmployeeRefreshToken, Long> {

    Optional<EmployeeRefreshToken> findByTokenHashAndRevokedIsFalse(String tokenHash);

    @Modifying
    @Query(
            "UPDATE EmployeeRefreshToken r SET r.revoked = true WHERE r.employeeId = :employeeId AND r.revoked = false")
    int revokeAllActiveForEmployee(@Param("employeeId") Long employeeId);

    @Query(
            """
            SELECT COUNT(r) FROM EmployeeRefreshToken r
            WHERE r.employeeId = :employeeId AND r.revoked = false AND r.expiresAt > :now
            """)
    long countActiveForEmployee(@Param("employeeId") Long employeeId, @Param("now") Instant now);

    Optional<EmployeeRefreshToken> findFirstByEmployeeIdAndRevokedIsFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            Long employeeId, Instant now);
}
