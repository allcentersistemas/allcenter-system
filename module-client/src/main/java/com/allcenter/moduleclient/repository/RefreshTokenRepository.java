package com.allcenter.moduleclient.repository;

import com.allcenter.moduleclient.model.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHashAndRevokedIsFalse(String tokenHash);

    @Modifying
    @Query(
            "UPDATE RefreshToken r SET r.revoked = true WHERE r.clientUserId = :clientUserId AND r.revoked = false")
    int revokeAllActiveForClient(@Param("clientUserId") Long clientUserId);
}
