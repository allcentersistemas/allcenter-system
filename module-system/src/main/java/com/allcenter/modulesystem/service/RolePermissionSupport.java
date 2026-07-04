package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.PermissionRuleDto;
import com.allcenter.modulesystem.model.Role;
import com.allcenter.modulesystem.model.RolePermission;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class RolePermissionSupport {

    private RolePermissionSupport() {}

    static void replacePermissions(Role role, List<PermissionRuleDto> rules) {
        if (rules == null) {
            return;
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
            String key = rule.action().trim() + ":" + rule.subject().trim();
            if (!seen.add(key)) {
                continue;
            }
            RolePermission perm = new RolePermission();
            perm.setRole(role);
            perm.setAction(rule.action().trim());
            perm.setSubject(rule.subject().trim());
            role.getPermissions().add(perm);
        }
    }
}
