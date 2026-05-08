package com.allcenter.moduleclient.service;

import com.allcenter.moduleclient.exception.BadRequestException;
import com.allcenter.moduleclient.exception.ConflictException;
import com.allcenter.moduleclient.exception.NotFoundException;
import com.allcenter.moduleclient.model.ClientUser;
import com.allcenter.moduleclient.model.dto.AuthSessionResponse;
import com.allcenter.moduleclient.model.dto.ChangePasswordRequest;
import com.allcenter.moduleclient.model.dto.ClientResponse;
import com.allcenter.moduleclient.model.dto.LoginRequest;
import com.allcenter.moduleclient.model.dto.RefreshTokenRequest;
import com.allcenter.moduleclient.model.dto.RegisterRequest;
import com.allcenter.moduleclient.repository.ClientUserRepository;
import com.allcenter.moduleclient.security.ClientUserDetails;
import com.allcenter.moduleclient.security.JwtProperties;
import com.allcenter.moduleclient.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final ClientUserRepository clientUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthSessionResponse login(LoginRequest request) {
        Authentication auth =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.email().trim(), request.password()));
        ClientUserDetails principal = (ClientUserDetails) auth.getPrincipal();
        String refresh = refreshTokenService.issue(principal.getClientUser().getId());
        return buildSession(principal, refresh);
    }

    @Transactional
    public AuthSessionResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (clientUserRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("El correo " + email + " ya esta registrado");
        }
        ClientUser client = new ClientUser();
        client.setEmail(email);
        client.setPassword(passwordEncoder.encode(request.password()));
        client.setDisplayName(request.displayName().trim());
        client.setCompanyName(request.companyName() != null ? request.companyName().trim() : null);
        client.setPhone(request.phone() != null ? request.phone().trim() : null);
        client.setTaxId(request.taxId() != null ? request.taxId().trim() : null);
        client.setActive(true);
        clientUserRepository.save(client);
        ClientUserDetails principal = new ClientUserDetails(client);
        String refresh = refreshTokenService.issue(client.getId());
        return buildSession(principal, refresh);
    }

    @Transactional
    public AuthSessionResponse refreshSession(RefreshTokenRequest request) {
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

    private AuthSessionResponse buildSession(ClientUserDetails principal, String refreshTokenRaw) {
        String access = jwtService.generateAccessToken(principal);
        return AuthSessionResponse.of(
                ClientResponse.from(principal.getClientUser()),
                access,
                refreshTokenRaw,
                jwtProperties.accessExpirationMs(),
                jwtProperties.refreshExpirationMs());
    }
}
