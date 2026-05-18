package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.config.MailProperties;
import com.allcenter.modulesystem.exception.BadRequestException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    public boolean isEnabled() {
        return mailProperties.enabled();
    }

    public void sendText(String to, String subject, String body) {
        send(to, subject, body, false);
    }

    public void sendHtml(String to, String subject, String htmlBody) {
        send(to, subject, htmlBody, true);
    }

    private void send(String to, String subject, String body, boolean html) {
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
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            String displayFrom =
                    mailProperties.fromName() != null && !mailProperties.fromName().isBlank()
                            ? String.format("%s <%s>", mailProperties.fromName(), from)
                            : from;
            helper.setFrom(displayFrom);
            helper.setTo(to.trim());
            helper.setSubject(subject);
            helper.setText(body, html);
            mailSender.send(message);
            log.info("Correo enviado a {}", to.trim());
        } catch (MessagingException | MailException ex) {
            log.error("No se pudo enviar correo a {}: {}", to, ex.getMessage());
            throw new BadRequestException("No se pudo enviar el correo: " + ex.getMessage());
        }
    }
}
