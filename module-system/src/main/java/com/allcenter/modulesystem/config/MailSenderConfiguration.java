package com.allcenter.modulesystem.config;

import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

/**
 * Configura SMTP de forma segura: solo activa autenticación si hay usuario y contraseña. Evita el error
 * {@code AuthenticationFailedException: no password specified} cuando SMTP_AUTH=true pero las credenciales
 * están vacías en el entorno.
 */
@Configuration
public class MailSenderConfiguration {

    @Bean
    @Primary
    JavaMailSender javaMailSender(
            @Value("${spring.mail.host:localhost}") String host,
            @Value("${spring.mail.port:587}") int port,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${SMTP_AUTH:#{null}}") String smtpAuthEnv,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}") String starttls) {

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host.trim());
        sender.setPort(port);

        String user = username == null ? "" : username.trim();
        String pass = password == null ? "" : password;
        boolean hasCredentials = StringUtils.hasText(user) && StringUtils.hasText(pass);

        boolean useAuth;
        if (smtpAuthEnv != null && !smtpAuthEnv.isBlank()) {
            useAuth = Boolean.parseBoolean(smtpAuthEnv.trim());
        } else {
            useAuth = hasCredentials;
        }
        if (useAuth && !hasCredentials) {
            useAuth = false;
        }

        if (useAuth) {
            sender.setUsername(user);
            sender.setPassword(pass);
        }

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", Boolean.toString(useAuth));
        props.put("mail.smtp.starttls.enable", starttls);
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        return sender;
    }
}
