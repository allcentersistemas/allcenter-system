package com.allcenter.modulesystem.config;

import com.allcenter.modulesystem.model.AppConfig;
import com.allcenter.modulesystem.repository.AppConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Garantiza fila singleton de configuración al arrancar. */
@Component
@RequiredArgsConstructor
public class AppConfigBootstrap implements ApplicationRunner {

    private final AppConfigRepository configRepository;

    @Value("${app.mail.enabled:false}")
    private boolean envMailEnabled;

    @Value("${app.mail.from:}")
    private String envMailFrom;

    @Value("${app.mail.from-name:AllCenter}")
    private String envMailFromName;

    @Value("${spring.mail.host:localhost}")
    private String envSmtpHost;

    @Value("${spring.mail.port:587}")
    private int envSmtpPort;

    @Value("${spring.mail.username:}")
    private String envSmtpUsername;

    @Value("${spring.mail.password:}")
    private String envSmtpPassword;

    @Value("${SMTP_AUTH:#{null}}")
    private String envSmtpAuth;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}")
    private String envSmtpStarttls;

    @Override
    public void run(ApplicationArguments args) {
        if (configRepository.findById(1L).isPresent()) {
            return;
        }
        AppConfig config = new AppConfig();
        config.setId(1L);
        config.setKardexEnabled(true);
        config.setMailEnabled(envMailEnabled);
        config.setMailFrom(envMailFrom == null ? "" : envMailFrom.trim());
        config.setMailFromName(envMailFromName == null ? "" : envMailFromName.trim());
        config.setSmtpHost(envSmtpHost == null ? "" : envSmtpHost.trim());
        config.setSmtpPort(envSmtpPort);
        config.setSmtpUsername(envSmtpUsername == null ? "" : envSmtpUsername.trim());
        config.setSmtpPassword(envSmtpPassword == null ? "" : envSmtpPassword);
        boolean auth =
                envSmtpAuth != null
                        && !envSmtpAuth.isBlank()
                        && Boolean.parseBoolean(envSmtpAuth.trim());
        config.setSmtpAuth(auth);
        config.setSmtpStarttls(
                envSmtpStarttls == null || Boolean.parseBoolean(envSmtpStarttls.trim()));
        configRepository.save(config);
    }
}
