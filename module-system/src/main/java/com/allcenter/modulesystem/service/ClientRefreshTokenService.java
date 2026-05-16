package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.exception.BadRequestException;
import com.allcenter.modulesystem.exception.NotFoundException;
import com.allcenter.modulesystem.model.ClientUser;
import com.allcenter.modulesystem.model.ClientRefreshToken;
import com.allcenter.modulesystem.repository.ClientUserRepository;
import com.allcenter.modulesystem.repository.ClientRefreshTokenRepository;
import com.allcenter.modulesystem.security.ClientUserDetails;
import com.allcenter.modulesystem.security.JwtProperties;
import com.allcenter.modulesystem.security.TokenHasher;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientRefreshTokenService {

    private final ClientRefreshTokenRepository refreshTokenRepository;
    private final ClientUserRepository clientUserRepository;
    private final JwtProperties jwtProperties;

    @Transactional
    public String issue(Long clientUserId) {
        String raw = TokenHasher.newOpaqueRefreshToken();
        String hash = TokenHasher.sha256Hex(raw);
        ClientRefreshToken rt = new ClientRefreshToken();
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
        ClientRefreshToken rt =
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
