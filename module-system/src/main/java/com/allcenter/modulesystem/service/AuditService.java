package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.model.AuditAction;
import com.allcenter.modulesystem.model.AuditEntry;
import com.allcenter.modulesystem.repository.AuditEntryRepository;
import com.allcenter.modulesystem.security.EmployeeUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEntryRepository auditEntryRepository;

    /**
     * Persiste auditoría en una transacción propia para que quede registrada aunque falle el commit
     * principal (p. ej. error de negocio tras un UPDATE).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEntityChange(
            AuditAction action, String entityType, String entityId, String details) {
        AuditEntry row = baseRow(action, entityType, entityId, details);
        applyActorFromSecurity(row);
        applyRequestMetadata(row);
        auditEntryRepository.save(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginSuccess(Long employeeId, String email) {
        AuditEntry row = baseRow(AuditAction.LOGIN_SUCCESS, "AUTH", String.valueOf(employeeId), null);
        row.setActorEmployeeId(employeeId);
        row.setActorEmail(email);
        applyRequestMetadata(row);
        auditEntryRepository.save(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFailure(String attemptedEmail, String reason) {
        AuditEntry row = baseRow(AuditAction.LOGIN_FAILURE, "AUTH", null, reason);
        row.setActorEmail(attemptedEmail);
        applyRequestMetadata(row);
        auditEntryRepository.save(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordClientLoginSuccess(Long clientUserId, String email) {
        AuditEntry row =
                baseRow(AuditAction.LOGIN_SUCCESS, "CLIENT_AUTH", String.valueOf(clientUserId), null);
        row.setActorClientUserId(clientUserId);
        row.setActorEmail(email);
        applyRequestMetadata(row);
        auditEntryRepository.save(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordClientLoginFailure(String attemptedLogin, String reason) {
        AuditEntry row = baseRow(AuditAction.LOGIN_FAILURE, "CLIENT_AUTH", null, reason);
        row.setActorEmail(attemptedLogin);
        applyRequestMetadata(row);
        auditEntryRepository.save(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordClientAccountCreated(Long clientUserId, String email) {
        AuditEntry row =
                baseRow(AuditAction.CREATE, "CLIENT_USER", String.valueOf(clientUserId), "Cuenta creada");
        row.setActorClientUserId(clientUserId);
        row.setActorEmail(email);
        applyRequestMetadata(row);
        auditEntryRepository.save(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordClientPasswordChanged(Long clientUserId, String email) {
        AuditEntry row =
                baseRow(
                        AuditAction.PASSWORD_CHANGED,
                        "CLIENT_AUTH",
                        String.valueOf(clientUserId),
                        "Contraseña actualizada");
        row.setActorClientUserId(clientUserId);
        row.setActorEmail(email);
        applyRequestMetadata(row);
        auditEntryRepository.save(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordClientLogoutAll(Long clientUserId, String email) {
        AuditEntry row =
                baseRow(
                        AuditAction.LOGOUT_ALL,
                        "CLIENT_AUTH",
                        String.valueOf(clientUserId),
                        "Sesiones cerradas en todos los dispositivos");
        row.setActorClientUserId(clientUserId);
        row.setActorEmail(email);
        applyRequestMetadata(row);
        auditEntryRepository.save(row);
    }

    public static String resolveClientPublicIp() {
        return currentRequest()
                .map(req -> ClientRequestAuditMetadata.from(req).clientIpPublic())
                .orElse(null);
    }

    public Page<AuditEntry> findClientAuthHistory(
            Long clientUserId, List<AuditAction> actions, Pageable pageable) {
        return auditEntryRepository.findClientAuthHistory(clientUserId, actions, pageable);
    }

    private static AuditEntry baseRow(
            AuditAction action, String entityType, String entityId, String details) {
        AuditEntry e = new AuditEntry();
        e.setAction(action);
        e.setEntityType(entityType);
        e.setEntityId(entityId);
        e.setDetails(details);
        return e;
    }

    private static void applyActorFromSecurity(AuditEntry row) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof EmployeeUserDetails principal) {
            row.setActorEmployeeId(principal.getEmployee().getId());
            row.setActorEmail(principal.getEmployee().getEmail());
        }
    }

    private static void applyRequestMetadata(AuditEntry row) {
        currentRequest()
                .ifPresent(
                        req -> {
                            ClientRequestAuditMetadata meta = ClientRequestAuditMetadata.from(req);
                            row.setDirectRemoteIp(meta.directRemoteIp());
                            row.setClientIpPublic(meta.clientIpPublic());
                            row.setClientIpLocal(meta.clientIpLocal());
                            row.setClientMacAddress(meta.clientMacAddress());
                            row.setDeviceName(meta.deviceName());
                            row.setDeviceId(meta.deviceId());
                            row.setForwardedForChain(meta.forwardedForChain());
                            row.setUserAgent(meta.userAgent());
                        });
    }

    private static Optional<HttpServletRequest> currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return Optional.ofNullable(servletAttrs.getRequest());
        }
        return Optional.empty();
    }
}
