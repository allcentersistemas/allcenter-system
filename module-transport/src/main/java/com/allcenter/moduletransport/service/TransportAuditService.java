package com.allcenter.moduletransport.service;

import com.allcenter.moduletransport.model.TransportAuditAction;
import com.allcenter.moduletransport.model.TransportAuditEntry;
import com.allcenter.moduletransport.repository.TransportAuditEntryRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class TransportAuditService {

    /** Opcional: el frontend de empleados puede enviar quién ejecutó la acción (sin JWT en este módulo). */
    public static final String HEADER_ACTOR_EMPLOYEE_ID = "X-Actor-Employee-Id";

    public static final String HEADER_ACTOR_EMAIL = "X-Actor-Email";

    private final TransportAuditEntryRepository repository;

    @Transactional
    public void record(
            TransportAuditAction action,
            String entityType,
            String entityId,
            String correlationId,
            String details) {
        TransportAuditEntry row = new TransportAuditEntry();
        row.setAction(action);
        row.setEntityType(entityType);
        row.setEntityId(entityId);
        row.setCorrelationId(correlationId);
        row.setDetails(details);
        applyActorHeaders(row);
        currentRequest()
                .ifPresent(
                        req -> {
                            TransportClientRequestMetadata meta = TransportClientRequestMetadata.from(req);
                            row.setClientIpPublic(meta.clientIpPublic());
                            row.setForwardedForChain(meta.forwardedForChain());
                            row.setUserAgent(meta.userAgent());
                        });
        repository.save(row);
    }

    private static void applyActorHeaders(TransportAuditEntry row) {
        currentRequest()
                .ifPresent(
                        req -> {
                            String idRaw = trim(req.getHeader(HEADER_ACTOR_EMPLOYEE_ID));
                            if (idRaw != null) {
                                try {
                                    row.setActorEmployeeId(Long.parseLong(idRaw));
                                } catch (NumberFormatException ignored) {
                                    // ignorar ID inválido
                                }
                            }
                            row.setActorEmail(trim(req.getHeader(HEADER_ACTOR_EMAIL)));
                        });
    }

    private static Optional<HttpServletRequest> currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return Optional.ofNullable(servletAttrs.getRequest());
        }
        return Optional.empty();
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
