package com.allcenter.modulesystem.listener;

import com.allcenter.modulesystem.config.ApplicationContextProvider;
import com.allcenter.modulesystem.model.AuditAction;
import com.allcenter.modulesystem.model.Role;
import com.allcenter.modulesystem.service.AuditService;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

public class RoleAuditEntityListener {

    @PostPersist
    public void afterPersist(Role role) {
        audit()
                .recordEntityChange(
                        AuditAction.CREATE,
                        "Role",
                        String.valueOf(role.getId()),
                        "name=" + role.getName());
    }

    @PostUpdate
    public void afterUpdate(Role role) {
        audit()
                .recordEntityChange(
                        AuditAction.UPDATE,
                        "Role",
                        String.valueOf(role.getId()),
                        "name=" + role.getName());
    }

    @PostRemove
    public void afterRemove(Role role) {
        audit()
                .recordEntityChange(
                        AuditAction.DELETE,
                        "Role",
                        String.valueOf(role.getId()),
                        "name=" + role.getName());
    }

    private static AuditService audit() {
        return ApplicationContextProvider.getBean(AuditService.class);
    }
}
