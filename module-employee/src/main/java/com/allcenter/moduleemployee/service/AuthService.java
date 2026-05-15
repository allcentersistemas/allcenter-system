package com.allcenter.moduleemployee.service;

import com.allcenter.moduleemployee.config.AuthEndpointProperties;
import com.allcenter.moduleemployee.config.FirstSetupProperties;
import com.allcenter.moduleemployee.config.RegistrationProperties;
import com.allcenter.moduleemployee.exception.BadRequestException;
import com.allcenter.moduleemployee.exception.ConflictException;
import com.allcenter.moduleemployee.exception.ForbiddenException;
import com.allcenter.moduleemployee.exception.NotFoundException;
import com.allcenter.moduleemployee.model.ContractType;
import com.allcenter.moduleemployee.model.DirectorySource;
import com.allcenter.moduleemployee.model.Employee;
import com.allcenter.moduleemployee.model.Role;
import com.allcenter.moduleemployee.model.dto.AuthSessionResponse;
import com.allcenter.moduleemployee.model.dto.ChangePasswordRequest;
import com.allcenter.moduleemployee.model.dto.EmployeeResponse;
import com.allcenter.moduleemployee.model.dto.FirstSetupRequest;
import com.allcenter.moduleemployee.model.dto.LoginRequest;
import com.allcenter.moduleemployee.model.dto.RefreshTokenRequest;
import com.allcenter.moduleemployee.model.dto.RegisterRequest;
import com.allcenter.moduleemployee.repository.EmployeeRepository;
import com.allcenter.moduleemployee.repository.RoleRepository;
import com.allcenter.moduleemployee.security.EmployeeUserDetails;
import com.allcenter.moduleemployee.security.JwtProperties;
import com.allcenter.moduleemployee.security.JwtService;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeService employeeService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final RegistrationProperties registrationProperties;
    private final FirstSetupProperties firstSetupProperties;
    private final AuthEndpointProperties authEndpointProperties;

    @Transactional(readOnly = true)
    public boolean isFirstSetupRequired() {
        return employeeRepository.count() == 0;
    }

    /**
     * Primera instalación: crea el único usuario inicial con rol MASTER y devuelve sesión (tokens).
     * Solo permitido mientras no exista ningún empleado.
     */
    @Transactional
    public AuthSessionResponse completeFirstSetup(String setupSecretHeader, FirstSetupRequest request) {
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
        String email = request.email().trim().toLowerCase();
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
        String refresh = refreshTokenService.issue(loaded.getId());
        return buildSession(principal, refresh);
    }

    @Transactional
    public AuthSessionResponse login(LoginRequest request) {
        Authentication auth =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.email().trim(), request.password()));
        EmployeeUserDetails principal = (EmployeeUserDetails) auth.getPrincipal();
        String refresh = refreshTokenService.issue(principal.getEmployee().getId());
        return buildSession(principal, refresh);
    }

    @Transactional
    public AuthSessionResponse register(RegisterRequest request) {
        if (!authEndpointProperties.registrationEnabled()) {
            throw new ForbiddenException("Public registration is disabled in this environment");
        }
        String email = request.email().trim().toLowerCase();
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
        EmployeeUserDetails principal = new EmployeeUserDetails(loaded);
        String refresh = refreshTokenService.issue(loaded.getId());
        return buildSession(principal, refresh);
    }

    @Transactional
    public AuthSessionResponse refreshSession(RefreshTokenRequest request) {
        EmployeeUserDetails principal =
                refreshTokenService.validateAndRevokeForRotation(request.refreshToken());
        String newRefresh = refreshTokenService.issue(principal.getEmployee().getId());
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
        if (!passwordEncoder.matches(password.trim(), e.getPassword())) {
            throw new BadRequestException("La contraseña no es correcta");
        }
    }

    private AuthSessionResponse buildSession(EmployeeUserDetails principal, String refreshTokenRaw) {
        String access = jwtService.generateAccessToken(principal);
        Employee emp =
                employeeRepository
                        .findByIdWithRoles(principal.getEmployee().getId())
                        .orElse(principal.getEmployee());
        return AuthSessionResponse.of(
                EmployeeResponse.from(emp),
                access,
                refreshTokenRaw,
                jwtProperties.accessExpirationMs(),
                jwtProperties.refreshExpirationMs());
    }
}
