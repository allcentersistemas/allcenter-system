package com.allcenter.moduleemployee.listener;

import com.allcenter.moduleemployee.security.EmployeeUserDetails;
import com.allcenter.moduleemployee.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginAuditEventListener {

    private final AuditService auditService;

    @EventListener
    public void onLoginSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof EmployeeUserDetails principal) {
            auditService.recordLoginSuccess(
                    principal.getEmployee().getId(), principal.getEmployee().getEmail());
        }
    }

    @EventListener
    public void onLoginFailure(AbstractAuthenticationFailureEvent event) {
        String attempted = event.getAuthentication().getName();
        Throwable ex = event.getException();
        String reason =
                ex != null && ex.getMessage() != null ? ex.getMessage() : "Authentication failed";
        auditService.recordLoginFailure(attempted, reason);
    }
}
