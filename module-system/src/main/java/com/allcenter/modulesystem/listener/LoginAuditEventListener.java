package com.allcenter.modulesystem.listener;

import com.allcenter.modulesystem.security.EmployeeUserDetails;
import com.allcenter.modulesystem.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginAuditEventListener {

    private final AuditService auditService;

    /** El login de empleados audita en {@link com.allcenter.modulesystem.service.EmployeeAuthService}. */

    @EventListener
    public void onLoginFailure(AbstractAuthenticationFailureEvent event) {
        String attempted = event.getAuthentication().getName();
        Throwable ex = event.getException();
        String reason =
                ex != null && ex.getMessage() != null ? ex.getMessage() : "Authentication failed";
        auditService.recordLoginFailure(attempted, reason);
    }
}
