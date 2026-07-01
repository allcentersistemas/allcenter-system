package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.exception.BadRequestException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final AppConfigService appConfigService;

    public boolean isEnabled() {
        return appConfigService.isMailEnabled();
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
        if (!appConfigService.isMailEnabled()) {
            log.debug("Correo deshabilitado; no se envía a {}", to);
            return;
        }
        if (to == null || to.isBlank()) {
            throw new BadRequestException("Destinatario de correo vacío");
        }
        String from = appConfigService.effectiveMailFrom();
        if (from == null || from.isBlank()) {
            throw new BadRequestException("Configure remitente (mailFrom) para enviar correos");
        }
        try {
            boolean multipart = attachments != null && !attachments.isEmpty();
            JavaMailSender mailSender = appConfigService.buildEffectiveMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, multipart, "UTF-8");
            String fromName = appConfigService.effectiveMailFromName();
            String displayFrom =
                    fromName != null && !fromName.isBlank()
                            ? String.format("%s <%s>", fromName, from)
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

    private static String friendlySmtpError(Exception ex) {
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (msg.contains("no password specified") || msg.contains("authenticationfailed")) {
            return "SMTP: falló la autenticación. Revisa SMTP_USERNAME, SMTP_PASSWORD y que APP_MAIL_FROM "
                    + "esté autorizado en tu proveedor (Gmail, SendGrid, etc.).";
        }
        if (msg.contains("sslhandshakeexception")
                || msg.contains("certificate_unknown")
                || msg.contains("no subject alternative names")) {
            return "SMTP: el certificado TLS no coincide con SMTP_HOST. Use el nombre del servidor de correo "
                    + "(p. ej. mail.tudominio.com), no la IP. Si el host es correcto, pruebe SMTP_PORT=465 "
                    + "o configure SMTP_SSL_TRUST con ese hostname.";
        }
        if (msg.contains("could not convert socket to tls")) {
            return "SMTP: no se pudo iniciar TLS. Revise SMTP_HOST (hostname con certificado válido), "
                    + "SMTP_PORT (587 STARTTLS o 465 SSL) y SMTP_STARTTLS.";
        }
        return null;
    }
}
