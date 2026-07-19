package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.PlanillaAiExtractDtos;
import com.allcenter.modulesystem.exception.BadRequestException;
import com.allcenter.modulesystem.model.AppConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanillaAiVisionService {

    private static final long MAX_BYTES = 8 * 1024 * 1024;
    private static final String EXTRACTION_PROMPT =
            """
            Puedes sacarme la información que tiene la hoja.

            Extrae SOLO estos campos de cada fila/pieza visible (medidas a mano o impresas):
            - Servicio de corte: Cantidad (Cant.), Largo en mm, Ancho en mm
            - Canto: L1, L2, A1, A2 (material de canto; vacío o NA si no hay)
            - Ranuras: distancia (ranuraDist), profundidad (ranuraProf), espesor/especial (ranuraEs), lado (ranuraLado: NA, L1, L2, A1 o A2)

            No inventes filas. Si un valor no se lee, déjalo vacío.
            Responde ÚNICAMENTE con JSON válido (sin markdown) con esta forma:
            {"filas":[{"cantidad":"1","largo":"600","ancho":"400","l1":"","l2":"","a1":"","a2":"","ranuraDist":"","ranuraProf":"","ranuraEs":"","ranuraLado":"","descripcion":""}]}
            """;

    private final AppConfigService appConfigService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    public PlanillaAiExtractDtos.ExtractResponse extractFromImage(MultipartFile file) {
        AppConfig config = appConfigService.requireAiVisionConfig();
        validateImage(file);

        String provider = normalizeProvider(config.getAiProvider());
        String model = resolveModel(provider, config.getAiModel());
        String mediaType = resolveMediaType(file);
        String base64;
        try {
            base64 = Base64.getEncoder().encodeToString(file.getBytes());
        } catch (Exception ex) {
            throw new BadRequestException("No se pudo leer la imagen: " + ex.getMessage());
        }

        String rawJson =
                switch (provider) {
                    case "openai" -> callOpenAi(config.getAiApiKey().trim(), model, mediaType, base64);
                    default -> callClaude(config.getAiApiKey().trim(), model, mediaType, base64);
                };

        List<PlanillaAiExtractDtos.DetalleRow> filas = parseFilas(rawJson);
        if (filas.isEmpty()) {
            throw new BadRequestException(
                    "La IA no encontró filas de corte (Cant./Largo/Ancho) en la imagen. Pruebe con otra foto más nítida.");
        }
        return new PlanillaAiExtractDtos.ExtractResponse(filas, provider, model);
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Seleccione una foto de la hoja de medidas.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("La imagen supera 8 MB. Use una foto más liviana.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        boolean okType =
                contentType.startsWith("image/")
                        || name.endsWith(".jpg")
                        || name.endsWith(".jpeg")
                        || name.endsWith(".png")
                        || name.endsWith(".webp")
                        || name.endsWith(".heic");
        if (!okType) {
            throw new BadRequestException("Formato no soportado. Use JPG, PNG o WEBP.");
        }
    }

    private static String normalizeProvider(String raw) {
        String p = raw == null ? "claude" : raw.trim().toLowerCase(Locale.ROOT);
        return "openai".equals(p) ? "openai" : "claude";
    }

    private static String resolveModel(String provider, String configured) {
        if (StringUtils.hasText(configured)) {
            return configured.trim();
        }
        return "openai".equals(provider) ? "gpt-4o" : "claude-sonnet-4-20250514";
    }

    private static String resolveMediaType(MultipartFile file) {
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType) && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return contentType;
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }

    private String callClaude(String apiKey, String model, String mediaType, String base64) {
        Map<String, Object> imageSource = new LinkedHashMap<>();
        imageSource.put("type", "base64");
        imageSource.put("media_type", mediaType);
        imageSource.put("data", base64);

        Map<String, Object> imageBlock = new LinkedHashMap<>();
        imageBlock.put("type", "image");
        imageBlock.put("source", imageSource);

        Map<String, Object> textBlock = new LinkedHashMap<>();
        textBlock.put("type", "text");
        textBlock.put("text", EXTRACTION_PROMPT);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", List.of(imageBlock, textBlock));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 4096);
        body.put("messages", List.of(message));

        String response = httpPostJson(
                "https://api.anthropic.com/v1/messages",
                body,
                Map.of(
                        "x-api-key",
                        apiKey,
                        "anthropic-version",
                        "2023-06-01",
                        "content-type",
                        "application/json"));
        return extractClaudeText(response);
    }

    private String callOpenAi(String apiKey, String model, String mediaType, String base64) {
        Map<String, Object> imageUrl = new LinkedHashMap<>();
        imageUrl.put("url", "data:" + mediaType + ";base64," + base64);

        Map<String, Object> imagePart = new LinkedHashMap<>();
        imagePart.put("type", "image_url");
        imagePart.put("image_url", imageUrl);

        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("type", "text");
        textPart.put("text", EXTRACTION_PROMPT);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", List.of(textPart, imagePart));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 4096);
        body.put("messages", List.of(message));

        String response = httpPostJson(
                "https://api.openai.com/v1/chat/completions",
                body,
                Map.of(
                        "Authorization",
                        "Bearer " + apiKey,
                        "content-type",
                        "application/json"));
        return extractOpenAiText(response);
    }

    private String httpPostJson(String url, Map<String, Object> body, Map<String, String> headers) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest.Builder builder =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(90))
                            .POST(HttpRequest.BodyPublishers.ofString(json));
            for (Map.Entry<String, String> h : headers.entrySet()) {
                builder.header(h.getKey(), h.getValue());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BadRequestException(friendlyProviderError(url, response.statusCode(), response.body()));
            }
            return response.body();
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Error llamando IA ({})", url, ex);
            throw new BadRequestException("No se pudo contactar el proveedor de IA: " + ex.getMessage());
        }
    }

    private static String friendlyProviderError(String url, int status, String body) {
        String snippet = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        if (snippet.length() > 280) {
            snippet = snippet.substring(0, 280) + "…";
        }
        String provider = url.contains("anthropic") ? "Claude" : "OpenAI";
        if (status == 401 || status == 403) {
            return provider + ": API key inválida o sin permiso (" + status + ").";
        }
        if (status == 429) {
            return provider + ": límite de uso alcanzado. Intente más tarde.";
        }
        return provider + " respondió error " + status + (snippet.isBlank() ? "" : ": " + snippet);
    }

    @SuppressWarnings("unchecked")
    private String extractClaudeText(String responseBody) {
        try {
            Map<String, Object> root = objectMapper.readValue(responseBody, new TypeReference<>() {});
            Object content = root.get("content");
            if (!(content instanceof List<?> blocks) || blocks.isEmpty()) {
                throw new BadRequestException("Respuesta vacía de Claude.");
            }
            StringBuilder sb = new StringBuilder();
            for (Object block : blocks) {
                if (block instanceof Map<?, ?> map && "text".equals(String.valueOf(map.get("type")))) {
                    Object text = map.get("text");
                    if (text != null) {
                        sb.append(text);
                    }
                }
            }
            if (sb.isEmpty()) {
                throw new BadRequestException("Claude no devolvió texto usable.");
            }
            return sb.toString();
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("No se pudo interpretar la respuesta de Claude.");
        }
    }

    @SuppressWarnings("unchecked")
    private String extractOpenAiText(String responseBody) {
        try {
            Map<String, Object> root = objectMapper.readValue(responseBody, new TypeReference<>() {});
            Object choices = root.get("choices");
            if (!(choices instanceof List<?> list) || list.isEmpty()) {
                throw new BadRequestException("Respuesta vacía de OpenAI.");
            }
            Object first = list.get(0);
            if (!(first instanceof Map<?, ?> choice)) {
                throw new BadRequestException("Respuesta inválida de OpenAI.");
            }
            Object message = choice.get("message");
            if (!(message instanceof Map<?, ?> msg)) {
                throw new BadRequestException("Respuesta inválida de OpenAI.");
            }
            Object content = msg.get("content");
            if (content == null || String.valueOf(content).isBlank()) {
                throw new BadRequestException("OpenAI no devolvió texto usable.");
            }
            return String.valueOf(content);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("No se pudo interpretar la respuesta de OpenAI.");
        }
    }

    private List<PlanillaAiExtractDtos.DetalleRow> parseFilas(String rawText) {
        String json = unwrapJson(rawText);
        try {
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<>() {});
            Object filasRaw = root.get("filas");
            if (!(filasRaw instanceof List<?> list)) {
                throw new BadRequestException("La IA no devolvió el JSON esperado (falta «filas»).");
            }
            List<PlanillaAiExtractDtos.DetalleRow> out = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                PlanillaAiExtractDtos.DetalleRow row = toRow(map);
                if (hasMeasure(row)) {
                    out.add(row);
                }
            }
            return out;
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("JSON IA no parseable: {}", truncate(rawText, 400));
            throw new BadRequestException(
                    "No se pudo interpretar el JSON de la IA. Pruebe otra foto o revise el modelo configurado.");
        }
    }

    private static PlanillaAiExtractDtos.DetalleRow toRow(Map<?, ?> map) {
        return new PlanillaAiExtractDtos.DetalleRow(
                str(map, "cantidad"),
                str(map, "largo", "largoVeta", "longitud"),
                str(map, "ancho", "width"),
                str(map, "l1"),
                str(map, "l2"),
                str(map, "a1"),
                str(map, "a2"),
                str(map, "ranuraDist", "randist", "dist"),
                str(map, "ranuraProf", "ranprof", "prof"),
                str(map, "ranuraEs", "ranes", "es"),
                str(map, "ranuraLado", "lado"),
                str(map, "descripcion", "observacion"));
    }

    private static boolean hasMeasure(PlanillaAiExtractDtos.DetalleRow row) {
        return StringUtils.hasText(row.cantidad())
                || StringUtils.hasText(row.largo())
                || StringUtils.hasText(row.ancho());
    }

    private static String str(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object v = map.get(key);
            if (v == null) {
                continue;
            }
            String s = String.valueOf(v).trim();
            if (!s.isEmpty() && !"null".equalsIgnoreCase(s) && !"NA".equalsIgnoreCase(s)) {
                return s;
            }
            if ("NA".equalsIgnoreCase(s)) {
                return "";
            }
        }
        return "";
    }

    private static String unwrapJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        String t = raw.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) {
                t = t.substring(firstNl + 1);
            }
            int fence = t.lastIndexOf("```");
            if (fence >= 0) {
                t = t.substring(0, fence);
            }
            t = t.trim();
        }
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return t.substring(start, end + 1);
        }
        return t;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
