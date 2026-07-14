package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.config.AuthEndpointProperties;
import com.allcenter.modulesystem.dto.ChangePasswordRequest;
import com.allcenter.modulesystem.dto.ClientAuthSessionResponse;
import com.allcenter.modulesystem.dto.ClientLoginEventResponse;
import com.allcenter.modulesystem.dto.ClientLoginHistoryResponse;
import com.allcenter.modulesystem.dto.ClientRegisterRequest;
import com.allcenter.modulesystem.dto.ClientResponse;
import com.allcenter.modulesystem.dto.LoginRequest;
import com.allcenter.modulesystem.dto.RefreshTokenRequest;
import com.allcenter.modulesystem.exception.BadRequestException;
import com.allcenter.modulesystem.exception.ConflictException;
import com.allcenter.modulesystem.exception.ForbiddenException;
import com.allcenter.modulesystem.exception.NotFoundException;
import com.allcenter.modulesystem.model.AuditAction;
import com.allcenter.modulesystem.model.ClientUser;
import com.allcenter.modulesystem.repository.ClientUserRepository;
import com.allcenter.modulesystem.security.ClientUserDetails;
import com.allcenter.modulesystem.security.JwtProperties;
import com.allcenter.modulesystem.security.JwtService;
import com.allcenter.modulesystem.support.ClientRequestInfo;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.allcenter.modulesystem.support.PasswordPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientAuthService {

    private static final Set<String> TIPOS_DOCUMENTO = Set.of("DNI", "CE", "PASAPORTE");

    private final ClientUserRepository clientUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthEndpointProperties authEndpointProperties;
    private final ClientRefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;

    public ClientAuthService(
            ClientUserRepository clientUserRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            AuthEndpointProperties authEndpointProperties,
            ClientRefreshTokenService refreshTokenService,
            @Qualifier("clientAuthenticationManager") AuthenticationManager authenticationManager,
            AuditService auditService) {
        this.clientUserRepository = clientUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.authEndpointProperties = authEndpointProperties;
        this.refreshTokenService = refreshTokenService;
        this.authenticationManager = authenticationManager;
        this.auditService = auditService;
    }

    @Transactional
    public ClientAuthSessionResponse login(LoginRequest request, ClientRequestInfo connection) {
        String login = request.username().trim();
        Optional<ClientUser> found =
                clientUserRepository
                        .findByEmailIgnoreCase(login)
                        .or(() -> clientUserRepository.findByUsernameIgnoreCase(login));
        if (found.isEmpty()) {
            auditService.recordClientLoginFailure(login, "Credenciales invalidas");
            throw new BadRequestException("Credenciales invalidas");
        }
        ClientUser client = found.get();
        try {
            Authentication auth =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(client.getEmail(), request.password()));
            ClientUserDetails principal = (ClientUserDetails) auth.getPrincipal();
            return completeLoginSession(principal, connection);
        } catch (RuntimeException ex) {
            auditService.recordClientLoginFailure(client.getEmail(), ex.getMessage());
            throw ex;
        }
    }

    private ClientAuthSessionResponse completeLoginSession(
            ClientUserDetails principal, ClientRequestInfo connection) {
        ClientUser client = principal.getClientUser();
        String clientIp = connection != null ? connection.clientIp() : AuditService.resolveClientPublicIp();
        client.setLastLoginAt(Instant.now());
        client.setLastLoginIp(clientIp);
        client.setLoginCount(client.getLoginCount() + 1);
        clientUserRepository.save(client);
        auditService.recordClientLoginSuccess(client.getId(), client.getEmail());
        String refresh = refreshTokenService.issue(client.getId(), connection);
        ClientUserDetails refreshed = new ClientUserDetails(client);
        return buildSession(refreshed, refresh);
    }

    @Transactional
    public ClientAuthSessionResponse register(ClientRegisterRequest request, ClientRequestInfo connection) {
        if (!authEndpointProperties.registrationEnabled()) {
            throw new ForbiddenException("Public registration is disabled in this environment");
        }
        String email = request.email().trim().toLowerCase();
        String username = normalizeUsername(request.username());
        PasswordPolicy.requireStrong(request.password());
        if (clientUserRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("El correo " + email + " ya esta registrado");
        }
        if (clientUserRepository.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("El usuario \"" + username + "\" ya esta en uso");
        }

        boolean juridica = Boolean.TRUE.equals(request.juridica());
        if (juridica) {
            String ruc = trimRequired(request.ruc(), "RUC");
            if (clientUserRepository.existsByRucIgnoreCase(ruc)) {
                throw new ConflictException("El RUC " + ruc + " ya esta registrado");
            }
        } else {
            String doc = trimRequired(request.numeroDocumento(), "numero de documento");
            if (clientUserRepository.existsByDocumentodeindentificacionIgnoreCase(doc)) {
                throw new ConflictException("El documento " + doc + " ya esta registrado");
            }
        }

        ClientUser client = new ClientUser();
        client.setEmail(email);
        client.setUsername(username);
        client.setPassword(passwordEncoder.encode(request.password()));
        client.setJuridica(juridica);
        client.setActive(true);

        if (juridica) {
            applyJuridicaProfile(client, request);
        } else {
            applyNaturalProfile(client, request);
        }

        clientUserRepository.save(client);
        auditService.recordClientAccountCreated(client.getId(), client.getEmail());
        ClientUserDetails principal = new ClientUserDetails(client);
        return completeLoginSession(principal, connection);
    }

    private void applyNaturalProfile(ClientUser client, ClientRegisterRequest request) {
        String fullName = trimRequired(request.displayName(), "nombre completo");
        client.setDisplayName(fullName);
        client.setNombre(null);
        client.setRazonSocial(null);
        client.setRuc(null);
        client.setPhone(trimRequired(request.phone(), "telefono"));
        String tipo = normalizeTipoDocumento(request.tipoDocumento());
        if (tipo == null) {
            throw new BadRequestException("Seleccione un tipo de documento valido (DNI, CE o Pasaporte)");
        }
        client.setTipoDocumento(tipo);
        client.setDocumentodeindentificacion(trimRequired(request.numeroDocumento(), "numero de documento"));
        client.setDireccion(trimRequired(request.direccion(), "direccion"));
        client.setCiudad(trimRequired(request.ciudad(), "ciudad"));
        client.setDistrito(trimRequired(request.distrito(), "distrito"));
        client.setDepartamento(trimRequired(request.departamento(), "departamento"));
    }

    private void applyJuridicaProfile(ClientUser client, ClientRegisterRequest request) {
        String razon = trimRequired(request.razonSocial(), "razon social");
        String ruc = trimRequired(request.ruc(), "RUC");
        String nombre = trimRequired(request.nombre(), "nombre de contacto");
        client.setRazonSocial(razon);
        client.setRuc(ruc);
        client.setNombre(nombre);
        client.setDisplayName(razon);
        client.setTipoDocumento(null);
        client.setDocumentodeindentificacion(null);
        client.setPhone(trimOptional(request.phone()));
        client.setDireccion(trimRequired(request.direccion(), "direccion"));
        client.setCiudad(trimRequired(request.ciudad(), "ciudad"));
        client.setDistrito(trimRequired(request.distrito(), "distrito"));
        client.setDepartamento(trimRequired(request.departamento(), "departamento"));
    }

    @Transactional
    public ClientAuthSessionResponse refreshSession(
            RefreshTokenRequest request, ClientRequestInfo connection) {
        ClientUserDetails principal =
                refreshTokenService.validateAndRevokeForRotation(request.refreshToken());
        String newRefresh = refreshTokenService.issue(principal.getClientUser().getId(), connection);
        return buildSession(principal, newRefresh);
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    @Transactional
    public void logoutAll(Long clientUserId) {
        ClientUser client =
                clientUserRepository
                        .findById(clientUserId)
                        .orElseThrow(() -> new NotFoundException("No existe un cliente con id " + clientUserId));
        refreshTokenService.revokeAllForClient(clientUserId);
        auditService.recordClientLogoutAll(clientUserId, client.getEmail());
    }

    public ClientResponse getProfile(long clientUserId) {
        ClientUser client =
                clientUserRepository
                        .findById(clientUserId)
                        .orElseThrow(() -> new NotFoundException("No existe un cliente con id " + clientUserId));
        return ClientResponse.from(client);
    }

    public ClientLoginHistoryResponse getLoginHistory(long clientUserId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        List<AuditAction> actions =
                List.of(
                        AuditAction.LOGIN_SUCCESS,
                        AuditAction.LOGIN_FAILURE,
                        AuditAction.CREATE,
                        AuditAction.PASSWORD_CHANGED,
                        AuditAction.LOGOUT_ALL);
        Page<ClientLoginEventResponse> result =
                auditService
                        .findClientAuthHistory(clientUserId, actions, PageRequest.of(safePage, safeSize))
                        .map(ClientLoginEventResponse::from);
        return new ClientLoginHistoryResponse(
                result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
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
        PasswordPolicy.requireStrong(request.newPassword());
        client.setPassword(passwordEncoder.encode(request.newPassword()));
        clientUserRepository.save(client);
        auditService.recordClientPasswordChanged(clientUserId, client.getEmail());
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

    private static String normalizeUsername(String raw) {
        String u = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (u.length() < 3) {
            throw new BadRequestException("El usuario debe tener al menos 3 caracteres");
        }
        if (!u.matches("^[a-z0-9._-]+$")) {
            throw new BadRequestException(
                    "El usuario solo puede contener letras, numeros, punto, guion y guion bajo");
        }
        return u;
    }

    private static String normalizeTipoDocumento(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT);
        if ("PASAPORTE".equals(t) || "PASSPORT".equals(t)) {
            return "PASAPORTE";
        }
        return TIPOS_DOCUMENTO.contains(t) ? t : null;
    }

    private static String trimRequired(String value, String fieldLabel) {
        String t = trimOptional(value);
        if (t == null) {
            throw new BadRequestException("El campo " + fieldLabel + " es obligatorio");
        }
        return t;
    }

    private static String trimOptional(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
