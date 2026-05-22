package com.allcenter.modulesystem.support;

import com.allcenter.modulesystem.model.Employee;
import com.allcenter.modulesystem.security.EmployeeUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedEmployeeResolver {

    public record Context(Long employeeId, Long branchId) {}

    public Optional<Context> resolve(HttpServletRequest request) {
        Context fromJwt = fromSecurityContext();
        if (fromJwt != null) {
            return Optional.of(fromJwt);
        }
        return fromActorHeaders(request);
    }

    private Optional<Context> fromActorHeaders(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        String idRaw = trim(request.getHeader(PaleAuditSourceCapture.HEADER_ACTOR_EMPLOYEE_ID));
        if (idRaw == null) {
            return Optional.empty();
        }
        try {
            long id = Long.parseLong(idRaw);
            return Optional.of(new Context(id, null));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private Context fromSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof EmployeeUserDetails details)) {
            return null;
        }
        Employee employee = details.getEmployee();
        if (employee == null || employee.getId() == null) {
            return null;
        }
        return new Context(employee.getId(), employee.getBranchId());
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
