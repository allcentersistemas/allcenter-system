package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.config.AuthEndpointProperties;
import com.allcenter.modulesystem.exception.BadRequestException;
import com.allcenter.modulesystem.exception.ConflictException;
import com.allcenter.modulesystem.exception.ForbiddenException;
import com.allcenter.modulesystem.exception.NotFoundException;
import com.allcenter.modulesystem.model.ClientUser;
import com.allcenter.modulesystem.dto.ClientAuthSessionResponse;
import com.allcenter.modulesystem.dto.ChangePasswordRequest;
import com.allcenter.modulesystem.dto.ClientResponse;
import com.allcenter.modulesystem.dto.LoginRequest;
import com.allcenter.modulesystem.dto.RefreshTokenRequest;
import com.allcenter.modulesystem.dto.ClientRegisterRequest;
import com.allcenter.modulesystem.repository.ClientUserRepository;
import com.allcenter.modulesystem.security.ClientUserDetails;
import com.allcenter.modulesystem.security.JwtProperties;
import com.allcenter.modulesystem.security.JwtService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientAuthService {

    private final ClientUserRepository clientUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthEndpointProperties authEndpointProperties;
    private final ClientRefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;

    public ClientAuthService(
            ClientUserRepository clientUserRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            AuthEndpointProperties authEndpointProperties,
            ClientRefreshTokenService refreshTokenService,
            @Qualifier("clientAuthenticationManager") AuthenticationManager authenticationManager) {
        this.clientUserRepository = clientUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.authEndpointProperties = authEndpointProperties;
        this.refreshTokenService = refreshTokenService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public ClientAuthSessionResponse login(LoginRequest request) {
        Authentication auth =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.username().trim(), request.password()));
        ClientUserDetails principal = (ClientUserDetails) auth.getPrincipal();
        String refresh = refreshTokenService.issue(principal.getClientUser().getId());
        return buildSession(principal, refresh);
    }

    @Transactional
    public ClientAuthSessionResponse register(ClientRegisterRequest request) {
        if (!authEndpointProperties.registrationEnabled()) {
            throw new ForbiddenException("Public registration is disabled in this environment");
        }
        String email = request.email().trim().toLowerCase();
        if (clientUserRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("El correo " + email + " ya esta registrado");
        }
        ClientUser client = new ClientUser();
        client.setEmail(email);
        client.setPassword(passwordEncoder.encode(request.password()));
        client.setDisplayName(request.displayName().trim());
        client.setPhone(request.phone() != null ? request.phone().trim() : null);
        client.setActive(true);
        clientUserRepository.save(client);
        ClientUserDetails principal = new ClientUserDetails(client);
        String refresh = refreshTokenService.issue(client.getId());
        return buildSession(principal, refresh);
    }

    @Transactional
    public ClientAuthSessionResponse refreshSession(RefreshTokenRequest request) {
        ClientUserDetails principal =
                refreshTokenService.validateAndRevokeForRotation(request.refreshToken());
        String newRefresh = refreshTokenService.issue(principal.getClientUser().getId());
        return buildSession(principal, newRefresh);
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    @Transactional
    public void logoutAll(Long clientUserId) {
        refreshTokenService.revokeAllForClient(clientUserId);
    }

    @Transactional
    public void changePassword(long clientUserId, ChangePasswordRequest request) {
        ClientUser client =
                clientUserRepository
                        .findById(clientUserId)
                        .orElseThrow(() -> new NotFoundException("No existe un cliente con id " + clientUserId));
        if (!passwordEncoder.matches(request.currentPassword(), client.getPassword())) {
            throw new BadRequestException("La contrasena actual no es correcta");
        }
        client.setPassword(passwordEncoder.encode(request.newPassword()));
        clientUserRepository.save(client);
    }

    private ClientAuthSessionResponse buildSession(ClientUserDetails principal, String refreshTokenRaw) {
        String access = jwtService.generateAccessToken(principal);
        return ClientAuthSessionResponse.of(
                ClientResponse.from(principal.getClientUser()),
                access,
                refreshTokenRaw,
                jwtProperties.accessExpirationMs(),
                jwtProperties.refreshExpirationMs());
    }
}
