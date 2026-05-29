package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.CreateRoleRequest;
import com.allcenter.modulesystem.dto.RolePatchRequest;
import com.allcenter.modulesystem.dto.RoleResponse;
import com.allcenter.modulesystem.service.RoleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("@portalAuth.canRead()")
    public ResponseEntity<List<RoleResponse>> list() {
        return ResponseEntity.ok(roleService.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@portalAuth.canRead()")
    public ResponseEntity<RoleResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getById(id));
    }

    @PostMapping
    @PreAuthorize("@portalAuth.canGestion()")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        RoleResponse body = roleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@portalAuth.canGestion()")
    public ResponseEntity<RoleResponse> patch(
            @PathVariable Long id, @Valid @RequestBody RolePatchRequest request) {
        return ResponseEntity.ok(roleService.patch(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@portalAuth.canDelete()")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
