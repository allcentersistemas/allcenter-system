package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.ClientAuthSessionResponse;
import com.allcenter.modulesystem.dto.ChangePasswordRequest;
import com.allcenter.modulesystem.dto.ClientResponse;
import com.allcenter.modulesystem.dto.LoginRequest;
import com.allcenter.modulesystem.dto.RefreshTokenRequest;
import com.allcenter.modulesystem.dto.ClientRegisterRequest;
import com.allcenter.modulesystem.security.ClientUserDetails;
import com.allcenter.modulesystem.service.ClientAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client/auth")
@RequiredArgsConstructor
public class ClientAuthController {

    private final ClientAuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ClientAuthSessionResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<ClientAuthSessionResponse> register(@Valid @RequestBody ClientRegisterRequest request) {
        ClientAuthSessionResponse body = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ClientAuthSessionResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshSession(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal ClientUserDetails principal) {
        authService.logoutAll(principal.getClientUser().getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<ClientResponse> me(@AuthenticationPrincipal ClientUserDetails principal) {
        return ResponseEntity.ok(ClientResponse.from(principal.getClientUser()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal ClientUserDetails principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getClientUser().getId(), request);
        return ResponseEntity.noContent().build();
    }
}
