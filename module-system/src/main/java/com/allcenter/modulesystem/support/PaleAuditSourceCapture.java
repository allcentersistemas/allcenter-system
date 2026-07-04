package com.allcenter.modulesystem.support;

import com.allcenter.modulesystem.model.Employee;
import com.allcenter.modulesystem.security.EmployeeUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Actor y origen de red para auditoría de pales (cabeceras del gateway + IP/UA del cliente),
 * mismo criterio que {@code module-system}.
 */
public final class PaleAuditSourceCapture {

    public static final String HEADER_ACTOR_EMPLOYEE_ID = "X-Actor-Employee-Id";
    public static final String HEADER_ACTOR_EMAIL = "X-Actor-Email";

    public record Captured(Long actorEmployeeId, String actorEmail, String sourceIp, String userAgent) {}

    private PaleAuditSourceCapture() {}

    public static Captured fromCurrentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return new Captured(null, null, null, null);
        }
        HttpServletRequest req = servletAttrs.getRequest();
        if (req == null) {
            return new Captured(null, null, null, null);
        }
        Long actorId = null;
        String idRaw = trim(req.getHeader(HEADER_ACTOR_EMPLOYEE_ID));
        if (idRaw != null) {
            try {
                actorId = Long.parseLong(idRaw);
            } catch (NumberFormatException ignored) {
                // ignorar
            }
        }
        String actorEmail = trim(req.getHeader(HEADER_ACTOR_EMAIL));
        if (actorId == null || actorEmail == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof EmployeeUserDetails principal) {
                Employee employee = principal.getEmployee();
                if (employee != null) {
                    if (actorId == null && employee.getId() != null) {
                        actorId = employee.getId();
                    }
                    if (actorEmail == null && employee.getEmail() != null && !employee.getEmail().isBlank()) {
                        actorEmail = employee.getEmail().trim();
                    }
                }
            }
        }
        AuditNet meta = AuditNet.from(req);
        return new Captured(actorId, actorEmail, meta.clientIpPublic(), meta.userAgent());
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** Metadatos de red (copia mínima del patrón TransportClientRequestMetadata). */
    private record AuditNet(String clientIpPublic, String userAgent) {
        private static final int UA_MAX = 2048;

        static AuditNet from(HttpServletRequest request) {
            String xff = trim(request.getHeader("X-Forwarded-For"));
            String clientPublic = resolveClientPublicIp(request, trim(request.getRemoteAddr()), xff);
            String ua = trim(request.getHeader("User-Agent"));
            if (ua != null && ua.length() > UA_MAX) {
                ua = ua.substring(0, UA_MAX);
            }
            return new AuditNet(truncate(clientPublic, 128), ua);
        }

        private static String resolveClientPublicIp(HttpServletRequest request, String directRemote, String xffChain) {
            String trueClient = trim(request.getHeader("True-Client-IP"));
            if (trueClient != null) {
                return firstIp(trueClient);
            }
            String cf = trim(request.getHeader("CF-Connecting-IP"));
            if (cf != null) {
                return firstIp(cf);
            }
            if (xffChain != null) {
                return firstIp(xffChain);
            }
            String realIp = trim(request.getHeader("X-Real-IP"));
            if (realIp != null) {
                return firstIp(realIp);
            }
            return truncate(directRemote, 128);
        }

        private static String firstIp(String headerValue) {
            if (headerValue == null || headerValue.isBlank()) {
                return null;
            }
            String first = headerValue.split(",")[0].trim();
            return first.isEmpty() ? null : truncate(first, 128);
        }

        private static String truncate(String s, int max) {
            if (s == null) {
                return null;
            }
            return s.length() <= max ? s : s.substring(0, max);
        }
    }
}
