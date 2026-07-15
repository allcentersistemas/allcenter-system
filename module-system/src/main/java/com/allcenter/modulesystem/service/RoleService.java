package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.exception.BadRequestException;
import com.allcenter.modulesystem.exception.ConflictException;
import com.allcenter.modulesystem.exception.NotFoundException;
import com.allcenter.modulesystem.model.Role;
import com.allcenter.modulesystem.dto.CreateRoleRequest;
import com.allcenter.modulesystem.dto.RolePatchRequest;
import com.allcenter.modulesystem.dto.RoleResponse;
import com.allcenter.modulesystem.repository.EmployeeRepository;
import com.allcenter.modulesystem.repository.RoleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public RoleResponse getById(Long id) {
        return roleRepository
                .findByIdWithPermissions(id)
                .map(RoleResponse::from)
                .orElseThrow(() -> new NotFoundException("No existe un rol con id " + id));
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> findAll() {
        return roleRepository.findAllWithPermissions().stream()
                .map(RoleResponse::from)
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
    }

    @Transactional
    public RoleResponse create(CreateRoleRequest request) {
        String normalized = request.name().trim().toUpperCase();
        if (roleRepository.existsByNameIgnoreCase(normalized)) {
            throw new ConflictException("Ya existe un rol con el nombre \"" + normalized + "\"");
        }
        Role role = new Role();
        role.setName(normalized);
        role.setDescription(
                request.description() != null ? request.description().trim() : null);
        RolePermissionSupport.replacePermissions(role, request.permissions());
        roleRepository.save(role);
        return RoleResponse.from(roleRepository.findByIdWithPermissions(role.getId()).orElse(role));
    }

    @Transactional
    public RoleResponse patch(Long id, RolePatchRequest request) {
        if (request.name() == null
                && request.description() == null
                && request.permissions() == null) {
            throw new BadRequestException(
                    "Debe enviar al menos el campo name, description o permissions para actualizar");
        }
        Role role =
                roleRepository
                        .findByIdWithPermissions(id)
                        .orElseThrow(() -> new NotFoundException("No existe un rol con id " + id));
        if (request.name() != null && !request.name().isBlank()) {
            String n = request.name().trim().toUpperCase();
            if (!n.equals(role.getName()) && roleRepository.existsByNameIgnoreCase(n)) {
                throw new ConflictException("Ya existe un rol con el nombre \"" + n + "\"");
            }
            role.setName(n);
        }
        if (request.description() != null) {
            role.setDescription(
                    request.description().trim().isEmpty() ? null : request.description().trim());
        }
        if (request.permissions() != null) {
            RolePermissionSupport.replacePermissions(role, request.permissions());
        }
        roleRepository.save(role);
        return RoleResponse.from(roleRepository.findByIdWithPermissions(id).orElse(role));
    }

    @Transactional
    public void delete(Long id) {
        if (!roleRepository.existsById(id)) {
            throw new NotFoundException("No existe un rol con id " + id);
        }
        if (employeeRepository.countEmployeesWithRole(id) > 0) {
            throw new ConflictException(
                    "No se puede eliminar el rol " + id + ": hay empleados que lo tienen asignado");
        }
        roleRepository.deleteById(id);
    }
}
