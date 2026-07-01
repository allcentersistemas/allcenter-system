package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.config.MailProperties;
import com.allcenter.modulesystem.dto.AppConfigDto;
import com.allcenter.modulesystem.dto.AppConfigUpdateRequest;
import com.allcenter.modulesystem.dto.KardexResetResult;
import com.allcenter.modulesystem.dto.MailTestRequest;
import com.allcenter.modulesystem.exception.BadRequestException;
import com.allcenter.modulesystem.model.AppConfig;
import com.allcenter.modulesystem.repository.AppConfigRepository;
import com.allcenter.modulesystem.repository.InvItemRepository;
import com.allcenter.modulesystem.repository.InvStockMovementRepository;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppConfigService {

    private final AppConfigRepository configRepository;
    private final InvStockMovementRepository movementRepository;
    private final InvItemRepository itemRepository;
    private final MailProperties envMailProperties;

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

    @Transactional(readOnly = true)
    public AppConfigDto getConfig() {
        return AppConfigDto.from(ensureConfigRow());
    }

    @Transactional(readOnly = true)
    public boolean isKardexEnabled() {
        return ensureConfigRow().isKardexEnabled();
    }

    @Transactional(readOnly = true)
    public boolean isMailEnabled() {
        AppConfig config = ensureConfigRow();
        if (StringUtils.hasText(config.getSmtpHost()) || StringUtils.hasText(config.getMailFrom())) {
            return config.isMailEnabled();
        }
        return envMailProperties.enabled();
    }

    @Transactional(readOnly = true)
    public String effectiveMailFrom() {
        AppConfig config = ensureConfigRow();
        if (StringUtils.hasText(config.getMailFrom())) {
            return config.getMailFrom().trim();
        }
        return envMailProperties.from();
    }

    @Transactional(readOnly = true)
    public String effectiveMailFromName() {
        AppConfig config = ensureConfigRow();
        if (StringUtils.hasText(config.getMailFromName())) {
            return config.getMailFromName().trim();
        }
        return envMailProperties.fromName();
    }

    @Transactional(readOnly = true)
    public String effectiveSmtpUsername() {
        AppConfig config = ensureConfigRow();
        if (StringUtils.hasText(config.getSmtpUsername())) {
            return config.getSmtpUsername().trim();
        }
        return envSmtpUsername == null ? "" : envSmtpUsername.trim();
    }

    @Transactional
    public AppConfigDto updateConfig(AppConfigUpdateRequest request) {
        AppConfig config = ensureConfigRow();
        if (request.kardexEnabled() != null) {
            config.setKardexEnabled(request.kardexEnabled());
        }
        if (request.mailEnabled() != null) {
            config.setMailEnabled(request.mailEnabled());
        }
        if (request.mailFrom() != null) {
            config.setMailFrom(trimMax(request.mailFrom(), 320));
        }
        if (request.mailFromName() != null) {
            config.setMailFromName(trimMax(request.mailFromName(), 128));
        }
        if (request.smtpHost() != null) {
            config.setSmtpHost(trimMax(request.smtpHost(), 256));
        }
        if (request.smtpPort() != null) {
            config.setSmtpPort(request.smtpPort());
        }
        if (request.smtpUsername() != null) {
            config.setSmtpUsername(trimMax(request.smtpUsername(), 320));
        }
        if (request.smtpPassword() != null && !request.smtpPassword().isBlank()) {
            config.setSmtpPassword(request.smtpPassword().trim());
        }
        if (request.smtpAuth() != null) {
            config.setSmtpAuth(request.smtpAuth());
        }
        if (request.smtpStarttls() != null) {
            config.setSmtpStarttls(request.smtpStarttls());
        }
        configRepository.save(config);
        return AppConfigDto.from(config);
    }

    @Transactional
    public KardexResetResult resetKardex() {
        long movements = movementRepository.count();
        long items = itemRepository.count();
        movementRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
        return new KardexResetResult(movements, items);
    }

    @Transactional
    public void sendTestMail(MailTestRequest request) {
        if (!isMailEnabled()) {
            throw new BadRequestException("El correo está desactivado. Actívelo en configuración.");
        }
        AppConfig config = ensureConfigRow();
        String envelopeFrom = resolveEnvelopeFrom();
        if (!StringUtils.hasText(envelopeFrom)) {
            throw new BadRequestException("Configure remitente (mailFrom) o usuario SMTP para enviar correos");
        }
        try {
            JavaMailSender sender = buildMailSender(config);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(formatFromHeader(envelopeFrom, effectiveMailFromName()));
            helper.setTo(request.to().trim());
            helper.setSubject("Prueba de correo — AllCenter");
            helper.setText(
                    "Este es un mensaje de prueba enviado desde Configuración del portal AllCenter.\n\n"
                            + "Si lo recibió, la configuración SMTP es correcta.",
                    false);
            sender.send(message);
            log.info("Correo de prueba enviado a {} desde {}", request.to().trim(), envelopeFrom);
        } catch (Exception ex) {
            throw new BadRequestException("No se pudo enviar el correo de prueba: " + ex.getMessage());
        }
    }

    JavaMailSender buildEffectiveMailSender() {
        return buildMailSender(ensureConfigRow(), 10_000);
    }

    JavaMailSender buildMailSenderForLargeAttachments() {
        return buildMailSender(ensureConfigRow(), 300_000);
    }

    void sendBackupNotification(
            String to, String subject, String plainBody, String htmlBody, List<MailFileAttachment> fileAttachments) {
        if (!isMailEnabled()) {
            throw new BadRequestException("El correo está desactivado. Actívelo en Configuración.");
        }
        String envelopeFrom = resolveEnvelopeFrom();
        if (!StringUtils.hasText(envelopeFrom)) {
            throw new BadRequestException("Configure remitente (mailFrom) o usuario SMTP para enviar correos");
        }
        if (to == null || to.isBlank()) {
            throw new BadRequestException("Destinatario de correo vacío");
        }
        try {
            boolean hasFiles = fileAttachments != null && !fileAttachments.isEmpty();
            JavaMailSender sender = buildMailSenderForLargeAttachments();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, hasFiles, "UTF-8");
            helper.setFrom(formatFromHeader(envelopeFrom, effectiveMailFromName()));
            helper.setTo(to.trim());
            helper.setSubject(subject);
            if (hasFiles) {
                helper.setText(plainBody, htmlBody);
                for (MailFileAttachment att : fileAttachments) {
                    if (att == null || att.path() == null || !java.nio.file.Files.isRegularFile(att.path())) {
                        continue;
                    }
                    helper.addAttachment(
                            att.filename(),
                            new FileSystemResource(att.path().toFile()),
                            att.contentType());
                }
            } else {
                helper.setText(plainBody, false);
            }
            sender.send(message);
            log.info(
                    "Correo de backup enviado a {} desde {} (adjuntos: {})",
                    to.trim(),
                    envelopeFrom,
                    hasFiles ? fileAttachments.size() : 0);
        } catch (Exception ex) {
            throw new BadRequestException("No se pudo enviar el correo: " + ex.getMessage());
        }
    }

    void sendHtmlMessage(String to, String subject, String htmlBody) {
        sendBackupNotification(to, subject, htmlBody.replaceAll("<[^>]+>", " "), htmlBody, List.of());
    }

    private String resolveEnvelopeFrom() {
        String smtpUser = effectiveSmtpUsername();
        if (StringUtils.hasText(smtpUser) && smtpUser.contains("@")) {
            return smtpUser;
        }
        return effectiveMailFrom();
    }

    private JavaMailSender buildMailSender(AppConfig config) {
        return buildMailSender(config, 10_000);
    }

    private JavaMailSender buildMailSender(AppConfig config, int timeoutMs) {
        String host =
                StringUtils.hasText(config.getSmtpHost()) ? config.getSmtpHost().trim() : envSmtpHost.trim();
        int port = config.getSmtpPort() > 0 ? config.getSmtpPort() : envSmtpPort;

        String user =
                StringUtils.hasText(config.getSmtpUsername())
                        ? config.getSmtpUsername().trim()
                        : (envSmtpUsername == null ? "" : envSmtpUsername.trim());
        String pass =
                StringUtils.hasText(config.getSmtpPassword())
                        ? config.getSmtpPassword()
                        : (envSmtpPassword == null ? "" : envSmtpPassword);

        boolean useAuth = config.isSmtpAuth();
        if (!StringUtils.hasText(config.getSmtpHost()) && envSmtpAuth != null && !envSmtpAuth.isBlank()) {
            useAuth = Boolean.parseBoolean(envSmtpAuth.trim());
        }
        boolean hasCredentials = StringUtils.hasText(user) && StringUtils.hasText(pass);
        if (useAuth && !hasCredentials) {
            useAuth = false;
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        if (useAuth) {
            sender.setUsername(user);
            sender.setPassword(pass);
        }

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", Boolean.toString(useAuth));
        String timeout = String.valueOf(Math.max(timeoutMs, 10_000));
        props.put("mail.smtp.connectiontimeout", timeout);
        props.put("mail.smtp.timeout", timeout);
        props.put("mail.smtp.writetimeout", timeout);

        boolean starttls = config.isSmtpStarttls();
        if (!StringUtils.hasText(config.getSmtpHost()) && envSmtpStarttls != null) {
            starttls = Boolean.parseBoolean(envSmtpStarttls.trim());
        }
        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.starttls.enable", "false");
        } else {
            props.put("mail.smtp.starttls.enable", Boolean.toString(starttls));
        }
        if (StringUtils.hasText(host)) {
            props.put("mail.smtp.ssl.trust", host);
        }
        return sender;
    }

    private AppConfig ensureConfigRow() {
        return configRepository.findById(1L).orElseGet(this::createDefaultConfig);
    }

    private AppConfig createDefaultConfig() {
        AppConfig config = new AppConfig();
        config.setId(1L);
        config.setKardexEnabled(true);
        config.setMailEnabled(envMailProperties.enabled());
        config.setMailFrom(envMailProperties.from() == null ? "" : envMailProperties.from());
        config.setMailFromName(envMailProperties.fromName() == null ? "" : envMailProperties.fromName());
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
        return configRepository.save(config);
    }

    private static String formatFromHeader(String from, String fromName) {
        if (StringUtils.hasText(fromName)) {
            return String.format("%s <%s>", fromName.trim(), from.trim());
        }
        return from.trim();
    }

    private static String trimMax(String raw, int max) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
