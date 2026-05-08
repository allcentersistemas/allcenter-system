package com.allcenter.moduleemployee.service;

import com.allcenter.moduleemployee.exception.BadRequestException;
import com.allcenter.moduleemployee.exception.ConflictException;
import com.allcenter.moduleemployee.exception.NotFoundException;
import com.allcenter.moduleemployee.model.Role;
import com.allcenter.moduleemployee.model.dto.CreateRoleRequest;
import com.allcenter.moduleemployee.model.dto.RolePatchRequest;
import com.allcenter.moduleemployee.model.dto.RoleResponse;
import com.allcenter.moduleemployee.repository.EmployeeRepository;
import com.allcenter.moduleemployee.repository.RoleRepository;
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
                .findById(id)
                .map(RoleResponse::from)
                .orElseThrow(() -> new NotFoundException("No existe un rol con id " + id));
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> findAll() {
        return roleRepository.findAll().stream()
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
        roleRepository.save(role);
        return RoleResponse.from(role);
    }

    @Transactional
    public RoleResponse patch(Long id, RolePatchRequest request) {
        if (request.name() == null && request.description() == null) {
            throw new BadRequestException("Debe enviar al menos el campo name o description para actualizar");
        }
        Role role =
                roleRepository
                        .findById(id)
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
        roleRepository.save(role);
        return RoleResponse.from(role);
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
