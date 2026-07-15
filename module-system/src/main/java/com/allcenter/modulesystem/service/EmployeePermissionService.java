package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.model.Employee;
import com.allcenter.modulesystem.model.Role;
import com.allcenter.modulesystem.model.RolePermission;
import com.allcenter.modulesystem.repository.EmployeeRepository;
import com.allcenter.modulesystem.security.EmployeeUserDetails;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Permisos CASL persistidos en {@code role_permissions} (acción + subject). */
@Service
@RequiredArgsConstructor
public class EmployeePermissionService {

    private final EmployeeRepository employeeRepository;

    public boolean currentEmployeeHas(String action, String subject) {
        return resolveCurrentEmployeeId()
                .map(id -> employeeRepository.findByIdWithRoles(id))
                .flatMap(opt -> opt)
                .map(employee -> employeeHas(employee, action, subject))
                .orElse(false);
    }

    public boolean currentEmployeeHasAction(String action) {
        String normalizedAction = normalize(action);
        if (normalizedAction.isBlank()) {
            return false;
        }
        return resolveCurrentEmployeeId()
                .map(id -> employeeRepository.findByIdWithRoles(id))
                .flatMap(opt -> opt)
                .map(employee -> employeeHasAction(employee, normalizedAction))
                .orElse(false);
    }

    private static boolean employeeHas(Employee employee, String action, String subject) {
        String normalizedAction = normalize(action);
        String normalizedSubject = normalize(subject);
        if (normalizedAction.isBlank() || normalizedSubject.isBlank()) {
            return false;
        }
        Set<Role> roles = employee.getRoles();
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        for (Role role : roles) {
            if (role == null || role.getPermissions() == null) {
                continue;
            }
            for (RolePermission perm : role.getPermissions()) {
                if (perm == null) {
                    continue;
                }
                if (isManageAll(perm)) {
                    return true;
                }
                if (normalizedAction.equals(normalize(perm.getAction()))
                        && normalizedSubject.equals(normalize(perm.getSubject()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean employeeHasAction(Employee employee, String action) {
        Set<Role> roles = employee.getRoles();
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        for (Role role : roles) {
            if (role == null || role.getPermissions() == null) {
                continue;
            }
            for (RolePermission perm : role.getPermissions()) {
                if (perm == null) {
                    continue;
                }
                if (isManageAll(perm) || action.equals(normalize(perm.getAction()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isManageAll(RolePermission perm) {
        return "manage".equals(normalize(perm.getAction())) && "all".equals(normalize(perm.getSubject()));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static Optional<Long> resolveCurrentEmployeeId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof EmployeeUserDetails details)) {
            return Optional.empty();
        }
        Employee employee = details.getEmployee();
        if (employee == null || employee.getId() == null) {
            return Optional.empty();
        }
        return Optional.of(employee.getId());
    }
}
