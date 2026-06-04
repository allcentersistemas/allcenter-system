package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.exception.BadRequestException;
import com.allcenter.modulesystem.exception.NotFoundException;
import com.allcenter.modulesystem.model.Employee;
import com.allcenter.modulesystem.model.EmployeeRefreshToken;
import com.allcenter.modulesystem.repository.EmployeeRepository;
import com.allcenter.modulesystem.repository.EmployeeRefreshTokenRepository;
import com.allcenter.modulesystem.security.EmployeeUserDetails;
import com.allcenter.modulesystem.security.JwtProperties;
import com.allcenter.modulesystem.security.TokenHasher;
import com.allcenter.modulesystem.support.ClientRequestInfo;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeRefreshTokenService {

    /** Sesiones simultáneas permitidas por empleado (p. ej. PC + móvil). */
    public static final int MAX_CONCURRENT_SESSIONS = 2;

    private final EmployeeRefreshTokenRepository refreshTokenRepository;
    private final EmployeeRepository employeeRepository;
    private final JwtProperties jwtProperties;

    @Transactional(readOnly = true)
    public boolean hasActiveSession(Long employeeId) {
        return refreshTokenRepository.countActiveForEmployee(employeeId, Instant.now()) > 0;
    }

    @Transactional
    public String issue(Long employeeId, ClientRequestInfo connection) {
        String raw = TokenHasher.newOpaqueRefreshToken();
        String hash = TokenHasher.sha256Hex(raw);
        Instant now = Instant.now();
        EmployeeRefreshToken rt = new EmployeeRefreshToken();
        rt.setEmployeeId(employeeId);
        rt.setTokenHash(hash);
        rt.setExpiresAt(now.plusMillis(jwtProperties.refreshExpirationMs()));
        rt.setRevoked(false);
        rt.setCreatedAt(now);
        rt.setLastActivityAt(now);
        if (connection != null) {
            rt.setClientIp(connection.clientIp());
            rt.setClientHostname(connection.clientHostname());
        }
        refreshTokenRepository.save(rt);
        markEmployeeConnected(employeeId, connection, now);
        return raw;
    }

    /**
     * Valida el refresh, comprueba caducidad, lo revoca (rotación) y devuelve el usuario para emitir
     * nuevos tokens. Debe llamarse a {@link #issue(Long, ClientRequestInfo)} después.
     */
    @Transactional
    public EmployeeUserDetails validateAndRevokeForRotation(
            String rawRefreshToken, ClientRequestInfo connection) {
        String hash = TokenHasher.sha256Hex(rawRefreshToken);
        EmployeeRefreshToken rt =
                refreshTokenRepository
                        .findByTokenHashAndRevokedIsFalse(hash)
                        .orElseThrow(
                                () ->
                                        new BadRequestException(
                                                "Refresh token inválido, revocado o ya sustituido"));
        if (rt.getExpiresAt().isBefore(Instant.now())) {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            clearStaleSessionIfNeeded(rt.getEmployeeId());
            throw new BadRequestException(
                    "Refresh token expirado; inicie sesión de nuevo con correo y contraseña");
        }
        rt.setRevoked(true);
        Instant now = Instant.now();
        rt.setLastActivityAt(now);
        refreshTokenRepository.save(rt);
        Employee e =
                employeeRepository
                        .findByIdWithRoles(rt.getEmployeeId())
                        .orElseThrow(() -> new NotFoundException("Empleado no encontrado"));
        if (!e.isActive()) {
            clearStaleSessionIfNeeded(e.getId());
            throw new BadRequestException("La cuenta está desactivada; no se pueden renovar tokens");
        }
        touchEmployeeSession(e.getId(), connection != null ? connection : clientInfoFromToken(rt), now);
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
                            clearStaleSessionIfNeeded(rt.getEmployeeId());
                        });
    }

    @Transactional
    public void revokeAllForEmployee(Long employeeId) {
        refreshTokenRepository.revokeAllActiveForEmployee(employeeId);
        clearEmployeeSession(employeeId);
    }

    @Transactional
    public void clearStaleSessionIfNeeded(Long employeeId) {
        if (!hasActiveSession(employeeId)) {
            clearEmployeeSession(employeeId);
        }
    }

    /** Revoca refresh caducados en toda la tabla (arranque y mantenimiento). */
    @Transactional
    public int revokeExpiredTokensGlobally() {
        return refreshTokenRepository.revokeAllExpired(Instant.now());
    }

    /**
     * Antes de un login nuevo: limpia tokens vencidos y, si ya hay {@link #MAX_CONCURRENT_SESSIONS}
     * sesiones activas, revoca la más antigua para dejar hueco a la nueva.
     */
    @Transactional
    public void replaceSessionForLogin(Long employeeId) {
        revokeExpiredTokensGlobally();
        trimActiveSessions(employeeId, MAX_CONCURRENT_SESSIONS - 1);
    }

    /**
     * Mantiene como máximo {@code maxActive} refresh tokens vigentes (revoca los más antiguos).
     */
    @Transactional
    public void trimActiveSessions(Long employeeId, int maxActive) {
        if (maxActive < 0) {
            return;
        }
        Instant now = Instant.now();
        List<EmployeeRefreshToken> active =
                refreshTokenRepository.findActiveForEmployeeOrderByCreatedAtAsc(employeeId, now);
        int excess = active.size() - maxActive;
        if (excess <= 0) {
            return;
        }
        for (int i = 0; i < excess; i++) {
            EmployeeRefreshToken rt = active.get(i);
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        }
        clearStaleSessionIfNeeded(employeeId);
    }

    @Transactional(readOnly = true)
    public ClientRequestInfo activeSessionInfo(Long employeeId) {
        return refreshTokenRepository
                .findFirstByEmployeeIdAndRevokedIsFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        employeeId, Instant.now())
                .map(this::clientInfoFromToken)
                .orElseGet(() -> new ClientRequestInfo(null, null));
    }

    private ClientRequestInfo clientInfoFromToken(EmployeeRefreshToken rt) {
        return new ClientRequestInfo(rt.getClientIp(), rt.getClientHostname());
    }

    private void markEmployeeConnected(Long employeeId, ClientRequestInfo connection, Instant now) {
        employeeRepository
                .findById(employeeId)
                .ifPresent(
                        e -> {
                            e.setSessionConnected(true);
                            if (connection != null) {
                                e.setSessionClientIp(connection.clientIp());
                                e.setSessionClientHostname(connection.clientHostname());
                            }
                            e.setSessionLastSeenAt(now);
                            employeeRepository.save(e);
                        });
    }

    private void touchEmployeeSession(Long employeeId, ClientRequestInfo connection, Instant now) {
        employeeRepository
                .findById(employeeId)
                .ifPresent(
                        e -> {
                            e.setSessionConnected(true);
                            e.setSessionLastSeenAt(now);
                            if (connection != null) {
                                if (connection.clientIp() != null) {
                                    e.setSessionClientIp(connection.clientIp());
                                }
                                if (connection.clientHostname() != null) {
                                    e.setSessionClientHostname(connection.clientHostname());
                                }
                            }
                            employeeRepository.save(e);
                        });
    }

    private void clearEmployeeSession(Long employeeId) {
        employeeRepository
                .findById(employeeId)
                .ifPresent(
                        e -> {
                            e.setSessionConnected(false);
                            e.setSessionClientIp(null);
                            e.setSessionClientHostname(null);
                            e.setSessionLastSeenAt(null);
                            employeeRepository.save(e);
                        });
    }
}
