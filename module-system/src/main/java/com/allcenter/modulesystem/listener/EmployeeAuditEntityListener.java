package com.allcenter.modulesystem.listener;

import com.allcenter.modulesystem.config.ApplicationContextProvider;
import com.allcenter.modulesystem.model.AuditAction;
import com.allcenter.modulesystem.model.Employee;
import com.allcenter.modulesystem.service.AuditService;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

public class EmployeeAuditEntityListener {

    @PostPersist
    public void afterPersist(Employee employee) {
        audit()
                .recordEntityChange(
                        AuditAction.CREATE,
                        "Employee",
                        String.valueOf(employee.getId()),
                        "email=" + employee.getEmail());
    }

    @PostUpdate
    public void afterUpdate(Employee employee) {
        audit()
                .recordEntityChange(
                        AuditAction.UPDATE,
                        "Employee",
                        String.valueOf(employee.getId()),
                        "email=" + employee.getEmail());
    }

    @PostRemove
    public void afterRemove(Employee employee) {
        audit()
                .recordEntityChange(
                        AuditAction.DELETE,
                        "Employee",
                        String.valueOf(employee.getId()),
                        "email=" + employee.getEmail());
    }

    private static AuditService audit() {
        return ApplicationContextProvider.getBean(AuditService.class);
    }
}
