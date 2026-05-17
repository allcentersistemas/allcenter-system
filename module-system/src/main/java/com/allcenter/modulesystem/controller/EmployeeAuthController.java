package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.EmployeeAuthSessionResponse;
import com.allcenter.modulesystem.dto.ChangePasswordRequest;
import com.allcenter.modulesystem.dto.EmployeeResponse;
import com.allcenter.modulesystem.dto.FirstSetupRequest;
import com.allcenter.modulesystem.dto.FirstSetupStatusResponse;
import com.allcenter.modulesystem.dto.LoginRequest;
import com.allcenter.modulesystem.dto.RefreshTokenRequest;
import com.allcenter.modulesystem.dto.EmployeeRegisterRequest;
import com.allcenter.modulesystem.dto.VerifyPasswordRequest;
import com.allcenter.modulesystem.security.EmployeeUserDetails;
import com.allcenter.modulesystem.repository.EmployeeRepository;
import com.allcenter.modulesystem.service.EmployeeAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class EmployeeAuthController {

    private final EmployeeAuthService authService;
    private final EmployeeRepository employeeRepository;

    /** Indica si aún puede ejecutarse el alta inicial (sin empleados en base). */
    @GetMapping("/first-setup/status")
    public ResponseEntity<FirstSetupStatusResponse> firstSetupStatus() {
        return ResponseEntity.ok(new FirstSetupStatusResponse(authService.isFirstSetupRequired()));
    }

    /**
     * Una sola vez: crea el primer usuario con rol MASTER y devuelve access + refresh. Requiere base
     * vacía. Si {@code app.first-setup.secret} está definido, envíe cabecera {@code X-First-Setup-Secret}.
     */
    @PostMapping("/first-setup")
    public ResponseEntity<EmployeeAuthSessionResponse> firstSetup(
            @RequestHeader(value = "X-First-Setup-Secret", required = false) String setupSecret,
            @Valid @RequestBody FirstSetupRequest request) {
        EmployeeAuthSessionResponse body = authService.completeFirstSetup(setupSecret, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/login")
    public ResponseEntity<EmployeeAuthSessionResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<EmployeeAuthSessionResponse> register(@Valid @RequestBody EmployeeRegisterRequest request) {
        EmployeeAuthSessionResponse body = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<EmployeeAuthSessionResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshSession(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal EmployeeUserDetails principal) {
        authService.logoutAll(principal.getEmployee().getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<EmployeeResponse> me(@AuthenticationPrincipal EmployeeUserDetails principal) {
        return employeeRepository
                .findByIdWithRoles(principal.getEmployee().getId())
                .map(EmployeeResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal EmployeeUserDetails principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getEmployee().getId(), request);
        return ResponseEntity.noContent().build();
    }

    /** Valida la contraseña del usuario autenticado sin cambiar sesión ni tokens. */
    @PostMapping("/verify-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> verifyPassword(
            @AuthenticationPrincipal EmployeeUserDetails principal,
            @Valid @RequestBody VerifyPasswordRequest request) {
        authService.verifyCurrentPassword(principal.getEmployee().getId(), request.password());
        return ResponseEntity.noContent().build();
    }
}
