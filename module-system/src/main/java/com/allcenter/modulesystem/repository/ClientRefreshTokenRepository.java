package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.ClientRefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRefreshTokenRepository extends JpaRepository<ClientRefreshToken, Long> {

    Optional<ClientRefreshToken> findByTokenHashAndRevokedIsFalse(String tokenHash);

    @Modifying
    @Query(
            "UPDATE ClientRefreshToken r SET r.revoked = true WHERE r.clientUserId = :clientUserId AND r.revoked = false")
    int revokeAllActiveForClient(@Param("clientUserId") Long clientUserId);
}
