package com.allcenter.moduleclient.service;

import com.allcenter.moduleclient.exception.BadRequestException;
import com.allcenter.moduleclient.exception.NotFoundException;
import com.allcenter.moduleclient.model.ClientUser;
import com.allcenter.moduleclient.model.RefreshToken;
import com.allcenter.moduleclient.repository.ClientUserRepository;
import com.allcenter.moduleclient.repository.RefreshTokenRepository;
import com.allcenter.moduleclient.security.ClientUserDetails;
import com.allcenter.moduleclient.security.JwtProperties;
import com.allcenter.moduleclient.security.TokenHasher;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final ClientUserRepository clientUserRepository;
    private final JwtProperties jwtProperties;

    @Transactional
    public String issue(Long clientUserId) {
        String raw = TokenHasher.newOpaqueRefreshToken();
        String hash = TokenHasher.sha256Hex(raw);
        RefreshToken rt = new RefreshToken();
        rt.setClientUserId(clientUserId);
        rt.setTokenHash(hash);
        rt.setExpiresAt(Instant.now().plusMillis(jwtProperties.refreshExpirationMs()));
        rt.setRevoked(false);
        refreshTokenRepository.save(rt);
        return raw;
    }

    @Transactional
    public ClientUserDetails validateAndRevokeForRotation(String rawRefreshToken) {
        String hash = TokenHasher.sha256Hex(rawRefreshToken);
        RefreshToken rt =
                refreshTokenRepository
                        .findByTokenHashAndRevokedIsFalse(hash)
                        .orElseThrow(
                                () ->
                                        new BadRequestException(
                                                "Refresh token invalido, revocado o ya sustituido"));
        if (rt.getExpiresAt().isBefore(Instant.now())) {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            throw new BadRequestException("Refresh token expirado; inicie sesion de nuevo");
        }
        rt.setRevoked(true);
        refreshTokenRepository.save(rt);
        ClientUser client =
                clientUserRepository
                        .findById(rt.getClientUserId())
                        .orElseThrow(() -> new NotFoundException("Cliente no encontrado"));
        if (!client.isActive()) {
            throw new BadRequestException("La cuenta esta desactivada");
        }
        return new ClientUserDetails(client);
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        String hash = TokenHasher.sha256Hex(rawRefreshToken);
        refreshTokenRepository
                .findByTokenHashAndRevokedIsFalse(hash)
                .ifPresent(
                        rt -> {
                            rt.setRevoked(true);
                            refreshTokenRepository.save(rt);
                        });
    }

    @Transactional
    public void revokeAllForClient(Long clientUserId) {
        refreshTokenRepository.revokeAllActiveForClient(clientUserId);
    }
}
