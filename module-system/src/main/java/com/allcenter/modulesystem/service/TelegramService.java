package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.exception.BadRequestException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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
        try {
            RestClient client = RestClient.create(API_BASE);
            Map<?, ?> body =
                    client.post()
                            .uri("/bot{token}/sendMessage", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("chat_id", chatId, "text", text, "parse_mode", "HTML"))
                            .retrieve()
                            .body(Map.class);
            if (body != null && Boolean.FALSE.equals(body.get("ok"))) {
                Object desc = body.get("description");
                throw new BadRequestException(
                        "Telegram rechazó el mensaje"
                                + (desc != null ? ": " + desc : ""));
            }
            log.info("Telegram enviado a chat {}", chatId);
        } catch (RestClientResponseException ex) {
            String detail = ex.getResponseBodyAsString();
            log.error("Telegram API error {}: {}", ex.getStatusCode().value(), detail);
            throw new BadRequestException(
                    "No se pudo enviar el mensaje de Telegram: "
                            + (StringUtils.hasText(detail) ? detail : ex.getMessage()));
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error al llamar Telegram API: {}", ex.getMessage());
            throw new BadRequestException("No se pudo enviar el mensaje de Telegram: " + ex.getMessage());
        }
    }
}
