package com.allcenter.moduleemployee.controller;

import com.allcenter.moduleemployee.model.dto.AuthSessionResponse;
import com.allcenter.moduleemployee.model.dto.ChangePasswordRequest;
import com.allcenter.moduleemployee.model.dto.EmployeeResponse;
import com.allcenter.moduleemployee.model.dto.FirstSetupRequest;
import com.allcenter.moduleemployee.model.dto.FirstSetupStatusResponse;
import com.allcenter.moduleemployee.model.dto.LoginRequest;
import com.allcenter.moduleemployee.model.dto.RefreshTokenRequest;
import com.allcenter.moduleemployee.model.dto.RegisterRequest;
import com.allcenter.moduleemployee.security.EmployeeUserDetails;
import com.allcenter.moduleemployee.service.AuthService;
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
public class AuthController {

    private final AuthService authService;

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
    public ResponseEntity<AuthSessionResponse> firstSetup(
            @RequestHeader(value = "X-First-Setup-Secret", required = false) String setupSecret,
            @Valid @RequestBody FirstSetupRequest request) {
        AuthSessionResponse body = authService.completeFirstSetup(setupSecret, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthSessionResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthSessionResponse body = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthSessionResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
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
        return ResponseEntity.ok(EmployeeResponse.from(principal.getEmployee()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal EmployeeUserDetails principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getEmployee().getId(), request);
        return ResponseEntity.noContent().build();
    }
}
