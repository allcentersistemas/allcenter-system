package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.exception.BadRequestException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Envío vía WhatsApp Cloud API (Meta).
 * Config global en {@code app_config}; destino = número del cliente ({@code whatsapp_phone} o {@code phone}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    private static final String GRAPH_BASE = "https://graph.facebook.com/v21.0";

    private final AppConfigService appConfigService;
    private final ObjectMapper objectMapper;

    public boolean isEnabled() {
        return appConfigService.isWhatsAppEnabled();
    }

    public void sendText(String phoneRaw, String text) {
        if (!appConfigService.isWhatsAppEnabled()) {
            log.debug("WhatsApp deshabilitado; no se envía a {}", phoneRaw);
            return;
        }
        String to = normalizePhone(phoneRaw);
        if (!StringUtils.hasText(to)) {
            log.debug("Número WhatsApp vacío; mensaje omitido");
            return;
        }
        if (!StringUtils.hasText(text)) {
            throw new BadRequestException("Mensaje de WhatsApp vacío");
        }
        String token = appConfigService.effectiveWhatsAppAccessToken();
        String phoneNumberId = appConfigService.effectiveWhatsAppPhoneNumberId();
        if (!StringUtils.hasText(token) || !StringUtils.hasText(phoneNumberId)) {
            throw new BadRequestException(
                    "Configure el token y Phone Number ID de WhatsApp en Gestión → Configuración");
        }
        postSendMessage(token.trim(), phoneNumberId.trim(), to, text.trim());
    }

    public void sendTextQuietly(String phoneRaw, String text) {
        try {
            sendText(phoneRaw, text);
        } catch (Exception ex) {
            log.error("No se pudo enviar WhatsApp a {}: {}", phoneRaw, ex.getMessage());
        }
    }

    /**
     * Deja solo dígitos. Acepta {@code +51…}, espacios y guiones.
     * Si tiene 9 dígitos (móvil PE sin código), antepone {@code 51}.
     */
    public static String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String digits = raw.trim().replaceAll("\\D+", "");
        if (digits.isEmpty()) {
            return "";
        }
        if (digits.length() == 9 && digits.startsWith("9")) {
            return "51" + digits;
        }
        return digits;
    }

    void postSendMessage(String token, String phoneNumberId, String to, String text) {
        String url = GRAPH_BASE + "/" + phoneNumberId + "/messages";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", "text");
        payload.put("text", Map.of("preview_url", false, "body", text));
        try {
            RestClient client = RestClient.create();
            Map<?, ?> body =
                    client.post()
                            .uri(url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + token)
                            .body(payload)
                            .retrieve()
                            .body(Map.class);
            if (body != null && body.get("error") != null) {
                throw new BadRequestException(friendlyWhatsAppError(String.valueOf(body.get("error"))));
            }
            log.info("WhatsApp enviado a {}", to);
        } catch (RestClientResponseException ex) {
            String detail = ex.getResponseBodyAsString();
            log.error("WhatsApp API error {}: {}", ex.getStatusCode().value(), detail);
            throw new BadRequestException(friendlyWhatsAppError(extractWhatsAppError(detail)));
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error al llamar WhatsApp API: {}", ex.getMessage());
            throw new BadRequestException("No se pudo enviar el mensaje de WhatsApp: " + ex.getMessage());
        }
    }

    private String extractWhatsAppError(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode err = root.get("error");
            if (err != null) {
                JsonNode msg = err.get("message");
                if (msg != null && !msg.isNull()) {
                    return msg.asString();
                }
                return err.toString();
            }
        } catch (Exception ignored) {
            // cuerpo crudo
        }
        return responseBody;
    }

    private static String friendlyWhatsAppError(String description) {
        String raw = description == null ? "" : description;
        String lower = raw.toLowerCase();
        if (lower.contains("not a valid whatsapp")
                || lower.contains("invalid parameter")
                || lower.contains("recipient")) {
            return "WhatsApp: número inválido o no registrado en WhatsApp. Use código de país "
                    + "(p. ej. 51987654321).";
        }
        if (lower.contains("(#131047)")
                || lower.contains("re-engagement")
                || lower.contains("24 hour")
                || lower.contains("outside the allowed window")) {
            return "WhatsApp: el cliente debe haber escrito al negocio en las últimas 24 h, "
                    + "o configure una plantilla aprobada en Meta Business.";
        }
        if (lower.contains("oauth")
                || lower.contains("access token")
                || lower.contains("expired")
                || lower.contains("invalid oauth")) {
            return "WhatsApp: token inválido o vencido. Genere uno nuevo en Meta for Developers "
                    + "y guárdelo en Configuración.";
        }
        if (lower.contains("phone number id") || lower.contains("does not exist")) {
            return "WhatsApp: Phone Number ID incorrecto. Revíselo en Meta → WhatsApp → API Setup.";
        }
        if (!raw.isBlank()) {
            return "No se pudo enviar el mensaje de WhatsApp: " + raw;
        }
        return "No se pudo enviar el mensaje de WhatsApp.";
    }
}
