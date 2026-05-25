package com.allcenter.modulesystem.config;

import com.allcenter.modulesystem.service.EmployeeRefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Al arrancar, revoca refresh tokens ya caducados para no bloquear logins. */
@Component
@RequiredArgsConstructor
public class EmployeeSessionCleanupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmployeeSessionCleanupRunner.class);

    private final EmployeeRefreshTokenService refreshTokenService;

    @Override
    public void run(ApplicationArguments args) {
        int n = refreshTokenService.revokeExpiredTokensGlobally();
        if (n > 0) {
            log.info("Sesiones: {} refresh token(s) caducado(s) marcados como revocados", n);
        }
    }
}
