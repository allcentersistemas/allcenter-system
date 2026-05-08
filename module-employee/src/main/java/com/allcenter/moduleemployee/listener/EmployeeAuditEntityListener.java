package com.allcenter.moduleemployee.listener;

import com.allcenter.moduleemployee.config.ApplicationContextProvider;
import com.allcenter.moduleemployee.model.AuditAction;
import com.allcenter.moduleemployee.model.Employee;
import com.allcenter.moduleemployee.service.AuditService;
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
