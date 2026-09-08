package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.exception.BadRequestException;
import java.net.URI;
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
 * Envío de mensajes vía Telegram Bot API.
 * Configuración global en {@code app_config}; destino por cliente en {@code client_users.telegram_chat_id}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramService {

    private static final String API_BASE = "https://api.telegram.org";

    private final AppConfigService appConfigService;
    private final ObjectMapper objectMapper;

    public boolean isEnabled() {
        return appConfigService.isTelegramEnabled();
    }

    /**
     * Envía un mensaje de texto. No-op si Telegram está desactivado.
     * No lanza si el destinatario está vacío (solo registra).
     */
    public void sendText(String chatId, String text) {
        if (!appConfigService.isTelegramEnabled()) {
            log.debug("Telegram deshabilitado; no se envía a chat {}", chatId);
            return;
        }
        if (!StringUtils.hasText(chatId)) {
            log.debug("Chat ID de Telegram vacío; mensaje omitido");
            return;
        }
        if (!StringUtils.hasText(text)) {
            throw new BadRequestException("Mensaje de Telegram vacío");
        }
        String token = appConfigService.effectiveTelegramBotToken();
        if (!StringUtils.hasText(token)) {
            throw new BadRequestException(
                    "Configure el token del bot de Telegram en Gestión → Configuración");
        }
        postSendMessage(token.trim(), chatId.trim(), text);
    }

    /**
     * Igual que {@link #sendText} pero nunca propaga errores (para hooks de negocio).
     */
    public void sendTextQuietly(String chatId, String text) {
        try {
            sendText(chatId, text);
        } catch (Exception ex) {
            log.error("No se pudo enviar Telegram a {}: {}", chatId, ex.getMessage());
        }
    }

    void postSendMessage(String token, String chatId, String text) {
        // No usar plantilla {token}: Spring codifica ":" del token y Telegram responde 404.
        URI uri = URI.create(API_BASE + "/bot" + token + "/sendMessage");
        try {
            RestClient client = RestClient.create();
            Map<?, ?> body =
                    client.post()
                            .uri(uri)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("chat_id", chatId, "text", text, "parse_mode", "HTML"))
                            .retrieve()
                            .body(Map.class);
            if (body != null && Boolean.FALSE.equals(body.get("ok"))) {
                throw new BadRequestException(friendlyTelegramError(String.valueOf(body.get("description"))));
            }
            log.info("Telegram enviado a chat {}", chatId);
        } catch (RestClientResponseException ex) {
            String detail = ex.getResponseBodyAsString();
            log.error("Telegram API error {}: {}", ex.getStatusCode().value(), detail);
            throw new BadRequestException(friendlyTelegramError(extractTelegramDescription(detail)));
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error al llamar Telegram API: {}", ex.getMessage());
            throw new BadRequestException("No se pudo enviar el mensaje de Telegram: " + ex.getMessage());
        }
    }

    private String extractTelegramDescription(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode desc = root.get("description");
            if (desc != null && !desc.isNull()) {
                return desc.asString();
            }
        } catch (Exception ignored) {
            // usar cuerpo crudo abajo
        }
        return responseBody;
    }

    private static String friendlyTelegramError(String description) {
        String raw = description == null ? "" : description;
        String lower = raw.toLowerCase();
        if (lower.contains("chat not found") || lower.contains("chat_id is empty")) {
            return "Telegram: chat no encontrado. Verifique el Chat ID y que haya iniciado "
                    + "conversación con el bot (abra el bot y pulse Iniciar / envíe /start).";
        }
        if (lower.contains("blocked by the user") || lower.contains("bot was blocked")) {
            return "Telegram: el usuario bloqueó el bot. Debe desbloquearlo e iniciar el chat de nuevo.";
        }
        if (lower.contains("bot can't initiate")
                || lower.contains("can't initiate conversation")
                || lower.contains("forbidden: bot")) {
            return "Telegram: el bot no puede escribir hasta que el usuario abra el chat "
                    + "y envíe /start a @"
                    + "su bot.";
        }
        if (lower.contains("unauthorized") || lower.contains("not found")) {
            return "Telegram: token inválido o URL incorrecta. Revise el token en Configuración "
                    + "(guarde de nuevo el token completo de BotFather).";
        }
        if (!raw.isBlank()) {
            return "No se pudo enviar el mensaje de Telegram: " + raw;
        }
        return "No se pudo enviar el mensaje de Telegram.";
    }
}
