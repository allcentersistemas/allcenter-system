package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.exception.ForbiddenException;
import com.allcenter.modulesystem.dto.AdminCreateEmployeeRequest;
import com.allcenter.modulesystem.dto.AdminResetPasswordRequest;
import com.allcenter.modulesystem.dto.EmployeeAdminPatchRequest;
import com.allcenter.modulesystem.dto.EmployeeCatalogItem;
import com.allcenter.modulesystem.dto.EmployeeResponse;
import com.allcenter.modulesystem.dto.EmployeeRolesRequest;
import com.allcenter.modulesystem.dto.EmployeeSelfPatchRequest;
import com.allcenter.modulesystem.security.EmployeeUserDetails;
import com.allcenter.modulesystem.service.EmployeeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping("/me")
    public ResponseEntity<EmployeeResponse> me(@AuthenticationPrincipal EmployeeUserDetails principal) {
        return ResponseEntity.ok(EmployeeResponse.from(principal.getEmployee()));
    }

    /** Catálogo de empleados activos por nombre de rol (p. ej. CHOFER). Cualquier usuario autenticado. */
    @GetMapping("/catalog/by-role/{roleName}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EmployeeCatalogItem>> catalogByRole(@PathVariable String roleName) {
        return ResponseEntity.ok(employeeService.listActiveCatalogByRole(roleName));
    }

    @PatchMapping("/me")
    public ResponseEntity<EmployeeResponse> patchMe(
            @AuthenticationPrincipal EmployeeUserDetails principal,
            @Valid @RequestBody EmployeeSelfPatchRequest request) {
        return ResponseEntity.ok(employeeService.patchSelf(principal.getEmployee().getId(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponse> getById(
            @AuthenticationPrincipal EmployeeUserDetails principal, @PathVariable Long id) {
        if (!principal.getEmployee().getId().equals(id)
                && !principal.getAuthorities().stream()
                        .anyMatch(
                                a ->
                                        "ROLE_ADMIN".equals(a.getAuthority())
                                                || "ROLE_MASTER".equals(a.getAuthority()))) {
            throw new ForbiddenException(
                    "No puede consultar el expediente del empleado con id "
                            + id
                            + "; solo el propio usuario, un administrador o MASTER pueden hacerlo");
        }
        return ResponseEntity.ok(employeeService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN','ADMIN_PRODUCCION')")
    public ResponseEntity<List<EmployeeResponse>> list(
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(employeeService.findAll(activeOnly, q));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN','ADMIN_PRODUCCION')")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long id, @Valid @RequestBody AdminResetPasswordRequest request) {
        boolean notify = Boolean.TRUE.equals(request.notifyByEmail());
        employeeService.resetPasswordByAdmin(id, request.newPassword(), notify);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN','ADMIN_PRODUCCION')")
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody AdminCreateEmployeeRequest request) {
        EmployeeResponse body = employeeService.createByAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN','ADMIN_PRODUCCION')")
    public ResponseEntity<EmployeeResponse> patchAdmin(
            @PathVariable Long id, @Valid @RequestBody EmployeeAdminPatchRequest request) {
        return ResponseEntity.ok(employeeService.patchAdmin(id, request));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN','ADMIN_PRODUCCION')")
    public ResponseEntity<EmployeeResponse> replaceRoles(
            @PathVariable Long id, @Valid @RequestBody EmployeeRolesRequest request) {
        return ResponseEntity.ok(employeeService.replaceRoles(id, request.roleIds()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN','ADMIN_PRODUCCION')")
    public ResponseEntity<Void> softDelete(@PathVariable Long id) {
        employeeService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
