package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.config.MailProperties;
import com.allcenter.modulesystem.exception.BadRequestException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Value("${SMTP_AUTH:#{null}}")
    private String smtpAuthEnv;

    public boolean isEnabled() {
        return mailProperties.enabled();
    }

    public void sendText(String to, String subject, String body) {
        send(to, subject, body, false, List.of());
    }

    public void sendHtml(String to, String subject, String htmlBody) {
        send(to, subject, htmlBody, true, List.of());
    }

    public void sendHtmlWithAttachments(String to, String subject, String htmlBody, List<MailAttachment> attachments) {
        send(to, subject, htmlBody, true, attachments == null ? List.of() : attachments);
    }

    private void send(String to, String subject, String body, boolean html, List<MailAttachment> attachments) {
        if (!mailProperties.enabled()) {
            log.debug("Correo deshabilitado (app.mail.enabled=false); no se envía a {}", to);
            return;
        }
        if (to == null || to.isBlank()) {
            throw new BadRequestException("Destinatario de correo vacío");
        }
        String from = mailProperties.from();
        if (from == null || from.isBlank()) {
            throw new BadRequestException("Configure app.mail.from para enviar correos");
        }
        validateSmtpCredentials();
        try {
            boolean multipart = attachments != null && !attachments.isEmpty();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, multipart, "UTF-8");
            String displayFrom =
                    mailProperties.fromName() != null && !mailProperties.fromName().isBlank()
                            ? String.format("%s <%s>", mailProperties.fromName(), from)
                            : from;
            helper.setFrom(displayFrom);
            helper.setTo(to.trim());
            helper.setSubject(subject);
            helper.setText(body, html);
            if (multipart) {
                for (MailAttachment att : attachments) {
                    if (att == null || att.filename() == null || att.content() == null) {
                        continue;
                    }
                    helper.addAttachment(att.filename(), () -> new java.io.ByteArrayInputStream(att.content()), att.contentType());
                }
            }
            mailSender.send(message);
            log.info("Correo enviado a {}", to.trim());
        } catch (MessagingException | MailException ex) {
            log.error("No se pudo enviar correo a {}: {}", to, ex.getMessage());
            String hint = friendlySmtpError(ex);
            throw new BadRequestException(
                    hint != null ? hint : "No se pudo enviar el correo: " + ex.getMessage());
        }
    }

    private void validateSmtpCredentials() {
        String user = smtpUsername == null ? "" : smtpUsername.trim();
        String pass = smtpPassword == null ? "" : smtpPassword;
        boolean wantsAuth =
                smtpAuthEnv != null && !smtpAuthEnv.isBlank() && Boolean.parseBoolean(smtpAuthEnv.trim());

        if (StringUtils.hasText(user) && !StringUtils.hasText(pass)) {
            throw new BadRequestException(
                    "SMTP: hay SMTP_USERNAME pero SMTP_PASSWORD está vacío. "
                            + "Añade SMTP_PASSWORD en tu archivo .env y reinicia module-system.");
        }
        if (wantsAuth && (!StringUtils.hasText(user) || !StringUtils.hasText(pass))) {
            throw new BadRequestException(
                    "SMTP: SMTP_AUTH=true pero faltan SMTP_USERNAME o SMTP_PASSWORD en .env.");
        }
    }

    private static String friendlySmtpError(Exception ex) {
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (msg.contains("no password specified") || msg.contains("authenticationfailed")) {
            return "SMTP: falló la autenticación. Revisa SMTP_USERNAME, SMTP_PASSWORD y que APP_MAIL_FROM "
                    + "esté autorizado en tu proveedor (Gmail, SendGrid, etc.).";
        }
        return null;
    }
}
