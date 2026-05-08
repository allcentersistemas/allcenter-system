package com.allcenter.moduleemployee.service;

import com.allcenter.moduleemployee.exception.BadRequestException;
import com.allcenter.moduleemployee.exception.NotFoundException;
import com.allcenter.moduleemployee.model.Employee;
import com.allcenter.moduleemployee.model.RefreshToken;
import com.allcenter.moduleemployee.repository.EmployeeRepository;
import com.allcenter.moduleemployee.repository.RefreshTokenRepository;
import com.allcenter.moduleemployee.security.EmployeeUserDetails;
import com.allcenter.moduleemployee.security.JwtProperties;
import com.allcenter.moduleemployee.security.TokenHasher;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final EmployeeRepository employeeRepository;
    private final JwtProperties jwtProperties;

    @Transactional
    public String issue(Long employeeId) {
        String raw = TokenHasher.newOpaqueRefreshToken();
        String hash = TokenHasher.sha256Hex(raw);
        RefreshToken rt = new RefreshToken();
        rt.setEmployeeId(employeeId);
        rt.setTokenHash(hash);
        rt.setExpiresAt(Instant.now().plusMillis(jwtProperties.refreshExpirationMs()));
        rt.setRevoked(false);
        refreshTokenRepository.save(rt);
        return raw;
    }

    /**
     * Valida el refresh, comprueba caducidad, lo revoca (rotación) y devuelve el usuario para emitir
     * nuevos tokens. Debe llamarse a {@link #issue(Long)} después para entregar un refresh nuevo al
     * cliente.
     */
    @Transactional
    public EmployeeUserDetails validateAndRevokeForRotation(String rawRefreshToken) {
        String hash = TokenHasher.sha256Hex(rawRefreshToken);
        RefreshToken rt =
                refreshTokenRepository
                        .findByTokenHashAndRevokedIsFalse(hash)
                        .orElseThrow(
                                () ->
                                        new BadRequestException(
                                                "Refresh token inválido, revocado o ya sustituido"));
        if (rt.getExpiresAt().isBefore(Instant.now())) {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            throw new BadRequestException(
                    "Refresh token expirado; inicie sesión de nuevo con correo y contraseña");
        }
        rt.setRevoked(true);
        refreshTokenRepository.save(rt);
        Employee e =
                employeeRepository
                        .findByIdWithRoles(rt.getEmployeeId())
                        .orElseThrow(() -> new NotFoundException("Empleado no encontrado"));
        if (!e.isActive()) {
            throw new BadRequestException("La cuenta está desactivada; no se pueden renovar tokens");
        }
        return new EmployeeUserDetails(e);
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
    public void revokeAllForEmployee(Long employeeId) {
        refreshTokenRepository.revokeAllActiveForEmployee(employeeId);
    }
}
