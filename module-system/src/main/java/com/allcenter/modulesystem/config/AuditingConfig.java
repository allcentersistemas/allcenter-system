package com.allcenter.modulesystem.config;

import com.allcenter.modulesystem.security.EmployeeUserDetails;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class AuditingConfig {

    @Bean
    AuditorAware<Long> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null
                    || !auth.isAuthenticated()
                    || !(auth.getPrincipal() instanceof EmployeeUserDetails principal)) {
                return Optional.empty();
            }
            Long id = principal.getEmployee().getId();
            return id != null ? Optional.of(id) : Optional.empty();
        };
    }
}
