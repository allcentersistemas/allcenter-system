package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.PermissionRuleDto;
import com.allcenter.modulesystem.model.Role;
import com.allcenter.modulesystem.model.RolePermission;
import com.allcenter.modulesystem.repository.RolePermissionRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class RolePermissionSupport {

    private RolePermissionSupport() {}

    static void replacePermissions(
            Role role, List<PermissionRuleDto> rules, RolePermissionRepository permissionRepository) {
        if (rules == null) {
            return;
        }
        if (role.getId() != null) {
            permissionRepository.deleteByRoleId(role.getId());
        }
        role.getPermissions().clear();
        Set<String> seen = new LinkedHashSet<>();
        for (PermissionRuleDto rule : rules) {
            if (rule == null
                    || rule.action() == null
                    || rule.action().isBlank()
                    || rule.subject() == null
                    || rule.subject().isBlank()) {
                continue;
            }
            String action = rule.action().trim().toLowerCase(Locale.ROOT);
            String subject = rule.subject().trim().toLowerCase(Locale.ROOT);
            String key = action + ":" + subject;
            if (!seen.add(key)) {
                continue;
            }
            RolePermission perm = new RolePermission();
            perm.setRole(role);
            perm.setAction(action);
            perm.setSubject(subject);
            role.getPermissions().add(perm);
        }
    }
}
