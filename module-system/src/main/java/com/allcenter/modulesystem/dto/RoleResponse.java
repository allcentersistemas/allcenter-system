package com.allcenter.modulesystem.dto;

import com.allcenter.modulesystem.model.Role;
import com.allcenter.modulesystem.model.RolePermission;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record RoleResponse(
        Long id,
        String name,
        String description,
        List<PermissionRuleDto> permissions,
        Long createdBy,
        Long lastModifiedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static RoleResponse from(Role role) {
        List<PermissionRuleDto> perms =
                role.getPermissions() == null
                        ? List.of()
                        : role.getPermissions().stream()
                                .map(
                                        p ->
                                                new PermissionRuleDto(
                                                        p.getAction(), p.getSubject()))
                                .sorted(
                                        Comparator.comparing(PermissionRuleDto::subject)
                                                .thenComparing(PermissionRuleDto::action))
                                .toList();
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                perms,
                role.getCreatedBy(),
                role.getLastModifiedBy(),
                role.getCreatedAt(),
                role.getUpdatedAt());
    }
}
