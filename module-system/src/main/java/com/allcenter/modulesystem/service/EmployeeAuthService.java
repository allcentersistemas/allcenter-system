package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.config.AuthEndpointProperties;
import com.allcenter.modulesystem.config.FirstSetupProperties;
import com.allcenter.modulesystem.config.RegistrationProperties;
import com.allcenter.modulesystem.exception.BadRequestException;
import com.allcenter.modulesystem.exception.ConflictException;
import com.allcenter.modulesystem.exception.ForbiddenException;
import com.allcenter.modulesystem.exception.NotFoundException;
import com.allcenter.modulesystem.support.ClientRequestInfo;
import com.allcenter.modulesystem.model.ContractType;
import com.allcenter.modulesystem.model.DirectorySource;
import com.allcenter.modulesystem.model.Employee;
import com.allcenter.modulesystem.model.Role;
import com.allcenter.modulesystem.dto.EmployeeAuthSessionResponse;
import com.allcenter.modulesystem.dto.ChangePasswordRequest;
import com.allcenter.modulesystem.dto.EmployeeResponse;
import com.allcenter.modulesystem.dto.FirstSetupRequest;
import com.allcenter.modulesystem.dto.LoginRequest;
import com.allcenter.modulesystem.dto.RefreshTokenRequest;
import com.allcenter.modulesystem.dto.EmployeeRegisterRequest;
import com.allcenter.modulesystem.repository.EmployeeRepository;
import com.allcenter.modulesystem.repository.RoleRepository;
import com.allcenter.modulesystem.security.EmployeeUserDetails;
import com.allcenter.modulesystem.security.JwtProperties;
import com.allcenter.modulesystem.security.JwtService;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeAuthService {

    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeService employeeService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final EmployeeRefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;
    private final RegistrationProperties registrationProperties;
    private final FirstSetupProperties firstSetupProperties;
    private final AuthEndpointProperties authEndpointProperties;

    public EmployeeAuthService(
            EmployeeRepository employeeRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            EmployeeService employeeService,
            JwtService jwtService,
            JwtProperties jwtProperties,
            EmployeeRefreshTokenService refreshTokenService,
            @Qualifier("employeeAuthenticationManager") AuthenticationManager authenticationManager,
            AuditService auditService,
            RegistrationProperties registrationProperties,
            FirstSetupProperties firstSetupProperties,
            AuthEndpointProperties authEndpointProperties) {
        this.employeeRepository = employeeRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.employeeService = employeeService;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.refreshTokenService = refreshTokenService;
        this.authenticationManager = authenticationManager;
        this.auditService = auditService;
        this.registrationProperties = registrationProperties;
        this.firstSetupProperties = firstSetupProperties;
        this.authEndpointProperties = authEndpointProperties;
    }

    @Transactional(readOnly = true)
    public boolean isFirstSetupRequired() {
        return employeeRepository.count() == 0;
    }

    /**
     * Primera instalación: crea el único usuario inicial con rol MASTER y devuelve sesión (tokens).
     * Solo permitido mientras no exista ningún empleado.
     */
    @Transactional
    public EmployeeAuthSessionResponse completeFirstSetup(String setupSecretHeader, FirstSetupRequest request) {
        if (!authEndpointProperties.firstSetupEnabled()) {
            throw new ForbiddenException("First setup is disabled in this environment");
        }
        if (employeeRepository.count() > 0) {
            throw new ConflictException(
                    "La instalación ya tiene usuarios; inicie sesión o pida a un administrador que le cree la cuenta");
        }
        if (firstSetupProperties.requiresSecret()) {
            String expected = firstSetupProperties.secret();
            if (setupSecretHeader == null || !expected.equals(setupSecretHeader.trim())) {
                throw new ForbiddenException(
                        "Cabecera X-First-Setup-Secret incorrecta o ausente; debe coincidir con app.first-setup.secret");
            }
        }
        String username = normalizeLoginUsername(request.username());
        String email = request.email().trim().toLowerCase();
        ensureUsernameAvailable(username);
        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("El correo " + email + " ya está registrado");
        }
        Role masterRole =
                roleRepository
                        .findByNameIgnoreCase("MASTER")
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Rol MASTER no existe; compruebe que RoleBootstrap haya ejecutado"));

        Employee employee = new Employee();
        employee.setEmployeeCode(employeeService.generateUniqueEmployeeCode());
        employee.setSamAccountName(username);
        employee.setEmail(email);
        employee.setDirectorySource(DirectorySource.LOCAL);
        employee.setPassword(passwordEncoder.encode(request.password()));
        employee.setFirstName(request.firstName().trim());
        employee.setLastName(request.lastName().trim());
        employee.setDocumentType(request.documentType());
        employee.setDocumentNumber(request.documentNumber().trim());
        employee.setHireDate(LocalDate.now());
        employee.setContractType(ContractType.INDEFINITE);
        employee.setJobTitle("System master");
        employee.setDepartment("System");
        employee.setWorkHoursPerWeek(40);
        employee.setRoles(new HashSet<>(List.of(masterRole)));
        employee.setActive(true);

        employeeRepository.save(employee);
        Employee loaded =
                employeeRepository
                        .findByIdWithRoles(employee.getId())
                        .orElseThrow(() -> new IllegalStateException("Empleado no recargado tras guardar"));
        EmployeeUserDetails principal = new EmployeeUserDetails(loaded);
        return completeLoginSession(principal, ClientRequestInfo.from(null));
    }

    public EmployeeAuthSessionResponse login(LoginRequest request, ClientRequestInfo connection) {
        String username = normalizeLoginUsername(request.username());
        try {
            Authentication auth =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(username, request.password()));
            EmployeeUserDetails principal = (EmployeeUserDetails) auth.getPrincipal();
            return completeLoginSession(principal, connection);
        } catch (RuntimeException ex) {
            auditService.recordLoginFailure(username, ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    protected EmployeeAuthSessionResponse completeLoginSession(
            EmployeeUserDetails principal, ClientRequestInfo connection) {
        Long employeeId = principal.getEmployee().getId();
        refreshTokenService.replaceSessionForLogin(employeeId);
        auditService.recordLoginSuccess(employeeId, principal.getEmployee().getEmail());
        String refresh = refreshTokenService.issue(employeeId, connection);
        return buildSession(principal, refresh);
    }

    @Transactional
    public EmployeeAuthSessionResponse register(EmployeeRegisterRequest request) {
        if (!authEndpointProperties.registrationEnabled()) {
            throw new ForbiddenException("Public registration is disabled in this environment");
        }
        String username = normalizeLoginUsername(request.username());
        String email = request.email().trim().toLowerCase();
        ensureUsernameAvailable(username);
        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("El correo " + email + " ya está registrado");
        }
        List<Role> roles = roleRepository.findAllById(request.roleIds());
        if (roles.size() != request.roleIds().size()) {
            throw new BadRequestException(
                    "Algún id en roleIds no existe; enviados "
                            + request.roleIds().size()
                            + ", roles encontrados "
                            + roles.size());
        }
        Set<String> allowed = registrationProperties.allowedRoleNamesSet();
        for (Role r : roles) {
            if (!allowed.contains(r.getName().toUpperCase())) {
                throw new BadRequestException(
                        "El rol \""
                                + r.getName()
                                + "\" no está permitido en el registro público. Permitidos: "
                                + String.join(", ", allowed));
            }
        }
        for (Role r : roles) {
            if ("MASTER".equalsIgnoreCase(r.getName())) {
                throw new BadRequestException(
                        "El rol MASTER no puede asignarse mediante el registro público");
            }
        }

        Employee employee = new Employee();
        employee.setEmployeeCode(employeeService.generateUniqueEmployeeCode());
        employee.setSamAccountName(username);
        employee.setEmail(email);
        employee.setDirectorySource(DirectorySource.LOCAL);
        employee.setPassword(passwordEncoder.encode(request.password()));
        employee.setFirstName(request.firstName().trim());
        employee.setSecondLastName(
                request.secondLastName() != null ? request.secondLastName().trim() : null);
        employee.setLastName(request.lastName().trim());
        employee.setDocumentType(request.documentType());
        employee.setDocumentNumber(request.documentNumber().trim());
        employee.setMobilePhone(
                request.mobilePhone() != null ? request.mobilePhone().trim() : null);
        employee.setBirthDate(request.birthDate());
        employee.setGender(request.gender());
        employee.setHireDate(LocalDate.now());
        employee.setContractType(ContractType.INDEFINITE);
        employee.setJobTitle("Pendiente asignación");
        employee.setDepartment("General");
        employee.setWorkHoursPerWeek(40);
        employee.setRoles(new HashSet<>(roles));
        employee.setActive(true);

        employeeRepository.save(employee);
        Employee loaded =
                employeeRepository
                        .findByIdWithRoles(employee.getId())
                        .orElseThrow(() -> new IllegalStateException("Empleado no recargado tras guardar"));
        return completeLoginSession(new EmployeeUserDetails(loaded), ClientRequestInfo.from(null));
    }

    private static String normalizeLoginUsername(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("El usuario es obligatorio");
        }
        return raw.trim();
    }

    private void ensureUsernameAvailable(String username) {
        if (employeeRepository.existsBySamAccountNameIgnoreCase(username)) {
            throw new ConflictException("El usuario \"" + username + "\" ya está en uso");
        }
    }

    @Transactional
    public EmployeeAuthSessionResponse refreshSession(
            RefreshTokenRequest request, ClientRequestInfo connection) {
        EmployeeUserDetails principal =
                refreshTokenService.validateAndRevokeForRotation(request.refreshToken(), connection);
        String newRefresh = refreshTokenService.issue(principal.getEmployee().getId(), connection);
        return buildSession(principal, newRefresh);
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    @Transactional
    public void logoutAll(Long employeeId) {
        refreshTokenService.revokeAllForEmployee(employeeId);
    }

    @Transactional
    public void changePassword(long employeeId, ChangePasswordRequest request) {
        Employee e =
                employeeRepository
                        .findById(employeeId)
                        .orElseThrow(() -> new NotFoundException("No existe un empleado con id " + employeeId));
        if (e.getPassword() == null || e.getPassword().isBlank()) {
            throw new BadRequestException(
                    "Esta cuenta no tiene contraseña local; use el acceso corporativo o contacte a un administrador.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), e.getPassword())) {
            throw new BadRequestException("La contraseña actual no es correcta");
        }
        e.setPassword(passwordEncoder.encode(request.newPassword()));
        employeeRepository.save(e);
    }

    /**
     * Comprueba la contraseña local del empleado autenticado (p. ej. confirmación antes de firmar un
     * registro). No emite nuevos tokens.
     */
    @Transactional(readOnly = true)
    public void verifyCurrentPassword(long employeeId, String password) {
        Employee e =
                employeeRepository
                        .findById(employeeId)
                        .orElseThrow(() -> new NotFoundException("No existe un empleado con id " + employeeId));
        if (e.getPassword() == null || e.getPassword().isBlank()) {
            throw new BadRequestException(
                    "Esta cuenta no tiene contraseña local; use el acceso corporativo o contacte a un administrador.");
        }
        String login = resolveLoginIdentifier(e);
        String raw = password == null ? "" : password;
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(login, raw));
        } catch (BadCredentialsException ex) {
            throw new BadRequestException("La contraseña no es correcta");
        }
    }

    /** Mismo identificador que usa el login (samAccountName, código EMP o email). */
    private static String resolveLoginIdentifier(Employee e) {
        if (e.getSamAccountName() != null && !e.getSamAccountName().isBlank()) {
            return e.getSamAccountName().trim();
        }
        if (e.getEmployeeCode() != null && !e.getEmployeeCode().isBlank()) {
            return e.getEmployeeCode().trim();
        }
        return e.getEmail().trim();
    }

    private EmployeeAuthSessionResponse buildSession(EmployeeUserDetails principal, String refreshTokenRaw) {
        String access = jwtService.generateAccessToken(principal);
        Employee emp =
                employeeRepository
                        .findByIdWithRoles(principal.getEmployee().getId())
                        .orElse(principal.getEmployee());
        return EmployeeAuthSessionResponse.of(
                EmployeeResponse.from(emp),
                access,
                refreshTokenRaw,
                jwtProperties.accessExpirationMs(),
                jwtProperties.refreshExpirationMs());
    }
}
