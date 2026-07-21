package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.AdminResetPasswordRequest;
import com.allcenter.modulesystem.dto.ClientAdminUpdateRequest;
import com.allcenter.modulesystem.dto.ClientCreateRequest;
import com.allcenter.modulesystem.dto.ClientLoginHistoryResponse;
import com.allcenter.modulesystem.dto.ClientResponse;
import com.allcenter.modulesystem.dto.PlanillaAiUsageDtos;
import com.allcenter.modulesystem.service.ClientAuthService;
import com.allcenter.modulesystem.service.ClientService;
import com.allcenter.modulesystem.service.PlanillaAiUsageService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gestion/clientes")
@RequiredArgsConstructor
public class GestionClienteController {

    private final ClientService clientService;
    private final ClientAuthService clientAuthService;
    private final PlanillaAiUsageService planillaAiUsageService;

    @GetMapping
    @PreAuthorize("@portalAuth.canGestionOrVentasGestion()")
    public ResponseEntity<List<ClientResponse>> list() {
        return ResponseEntity.ok(clientService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@portalAuth.canRead()")
    public ResponseEntity<ClientResponse> getById(@PathVariable long id) {
        return ResponseEntity.ok(clientService.getById(id));
    }

    @GetMapping("/{id}/login-history")
    @PreAuthorize("@portalAuth.canGestionOrVentasGestion()")
    public ResponseEntity<ClientLoginHistoryResponse> loginHistory(
            @PathVariable long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        clientService.getById(id);
        return ResponseEntity.ok(clientAuthService.getLoginHistory(id, page, size));
    }

    @GetMapping("/{id}/ai-usage")
    @PreAuthorize("@portalAuth.canGestionOrVentasGestion()")
    public ResponseEntity<PlanillaAiUsageDtos.ClientUsageResponse> aiUsage(
            @PathVariable long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        clientService.getById(id);
        return ResponseEntity.ok(planillaAiUsageService.getClientUsage(id, page, size));
    }

    @PostMapping
    @PreAuthorize("@portalAuth.canGestionOrVentasGestion()")
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clientService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@portalAuth.canGestionOrVentasGestion()")
    public ResponseEntity<ClientResponse> update(
            @PathVariable long id, @Valid @RequestBody ClientAdminUpdateRequest request) {
        return ResponseEntity.ok(clientService.updateAdmin(id, request));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("@portalAuth.canGestionOrVentasGestion()")
    public ResponseEntity<Void> resetPassword(
            @PathVariable long id, @Valid @RequestBody AdminResetPasswordRequest request) {
        clientService.resetPasswordByAdmin(id, request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@portalAuth.canGestionOrVentasGestion()")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        clientService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
