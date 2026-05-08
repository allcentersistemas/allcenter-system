package com.allcenter.moduleemployee.model.dto;

import com.allcenter.moduleemployee.model.Role;
import java.time.LocalDateTime;

public record RoleResponse(
        Long id,
        String name,
        String description,
        Long createdBy,
        Long lastModifiedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getCreatedBy(),
                role.getLastModifiedBy(),
                role.getCreatedAt(),
                role.getUpdatedAt());
    }
}
