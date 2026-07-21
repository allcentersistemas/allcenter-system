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
            Eres un extractor de hojas de medidas de corte de tableros (melamina/MDF).

            UNA HOJA VÁLIDA muestra filas de piezas con al menos:
            - Cantidad (Cant.), Largo en mm y Ancho en mm
            - Opcionalmente cantos L1, L2, A1, A2 y ranuras (distancia, profundidad, espesor, lado)

            NO ES VÁLIDA: selfie, persona, factura, ticket, captura de Excel sin medidas de corte,
            menú de app, foto borrosa ilegible, documento sin Cant./Largo/Ancho, paisaje, etc.

            Responde ÚNICAMENTE con JSON válido (sin markdown):
            - Si la imagen ES una hoja de medidas legible:
              {"valido":true,"filas":[{"cantidad":"1","largo":"600","ancho":"400","l1":"","l2":"","a1":"","a2":"","ranuraDist":"","ranuraProf":"","ranuraEs":"","ranuraLado":"","descripcion":""}]}
            - Si NO es válida o no se pueden leer medidas:
              {"valido":false,"motivo":"explicación breve en español","filas":[]}

            Reglas: no inventes filas. Si un valor no se lee, déjalo vacío. Cantos vacíos o NA → "".
            """;

    private final AppConfigService appConfigService;
    private final PlanillaAiUsageService usageService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    public PlanillaAiExtractDtos.ExtractResponse extractFromImage(Long clientUserId, MultipartFile file) {
        AppConfig config = appConfigService.requireAiVisionConfig();
        validateImage(file);

        String provider = normalizeProvider(config.getAiProvider());
        String model = resolveModel(provider, config.getAiModel());
        String filename = file.getOriginalFilename();
        long bytes = file.getSize();

        if (clientUserId != null) {
            try {
                usageService.assertWithinDailyLimit(clientUserId);
            } catch (BadRequestException ex) {
                usageService.logUsage(
                        clientUserId,
                        provider,
                        model,
                        false,
                        0,
                        null,
                        null,
                        ex.getMessage(),
                        filename,
                        bytes);
                throw ex;
            }
        }

        String mediaType = resolveMediaType(file);
        String base64;
        try {
            base64 = Base64.getEncoder().encodeToString(file.getBytes());
        } catch (Exception ex) {
            throw new BadRequestException("No se pudo leer la imagen: " + ex.getMessage());
        }

        ProviderResult providerResult;
        try {
            providerResult =
                    switch (provider) {
                        case "openai" ->
                                callOpenAi(config.getAiApiKey().trim(), model, mediaType, base64);
                        default -> callClaude(config.getAiApiKey().trim(), model, mediaType, base64);
                    };
        } catch (BadRequestException ex) {
            usageService.logUsage(
                    clientUserId,
                    provider,
                    model,
                    false,
                    0,
                    null,
                    null,
                    ex.getMessage(),
                    filename,
                    bytes);
            throw ex;
        }

        ParsedExtraction parsed;
        try {
            parsed = parseExtraction(providerResult.text());
        } catch (BadRequestException ex) {
            usageService.logUsage(
                    clientUserId,
                    provider,
                    model,
                    false,
                    0,
                    providerResult.inputTokens(),
                    providerResult.outputTokens(),
                    ex.getMessage(),
                    filename,
                    bytes);
            throw ex;
        }

        if (!parsed.valido() || parsed.filas().isEmpty()) {
            String motivo =
                    StringUtils.hasText(parsed.motivo())
                            ? parsed.motivo()
                            : "La imagen no parece una hoja de medidas válida o no se leyeron Cant./Largo/Ancho.";
            usageService.logUsage(
                    clientUserId,
                    provider,
                    model,
                    false,
                    0,
                    providerResult.inputTokens(),
                    providerResult.outputTokens(),
                    motivo,
                    filename,
                    bytes);
            throw new BadRequestException(motivo);
        }

        usageService.logUsage(
                clientUserId,
                provider,
                model,
                true,
                parsed.filas().size(),
                providerResult.inputTokens(),
                providerResult.outputTokens(),
                null,
                filename,
                bytes);
        return new PlanillaAiExtractDtos.ExtractResponse(parsed.filas(), provider, model);
    }

    /** Compatibilidad: sin cliente no aplica rate limit ni registro de uso. */
    public PlanillaAiExtractDtos.ExtractResponse extractFromImage(MultipartFile file) {
        return extractFromImage(null, file);
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
        if ("openai".equals(provider)) {
            return resolveOpenAiModel(configured);
        }
        return resolveClaudeModel(configured);
    }

    private static String resolveClaudeModel(String configured) {
        if (!StringUtils.hasText(configured)) {
            return "claude-sonnet-5";
        }
        String raw = configured.trim();
        String key = raw.toLowerCase(Locale.ROOT).replace('_', '-').replaceAll("\\s+", " ").trim();
        return switch (key) {
            case "sonnet", "sonnet 5", "sonnet5", "claude sonnet 5", "claude-sonnet-5" -> "claude-sonnet-5";
            case "sonnet 4.6", "sonnet4.6", "claude sonnet 4.6", "claude-sonnet-4-6", "claude-sonnet-4.6" ->
                    "claude-sonnet-4-6";
            case "sonnet 4.5", "sonnet4.5", "claude sonnet 4.5", "claude-sonnet-4-5", "claude-sonnet-4.5" ->
                    "claude-sonnet-4-5";
            case "haiku", "haiku 4.5", "claude haiku 4.5", "claude-haiku-4-5" -> "claude-haiku-4-5";
            case "opus", "opus 4.8", "claude opus 4.8", "claude-opus-4-8" -> "claude-opus-4-8";
            default -> raw.contains(" ") ? raw.toLowerCase(Locale.ROOT).replace(' ', '-') : raw;
        };
    }

    private static String resolveOpenAiModel(String configured) {
        if (!StringUtils.hasText(configured)) {
            return "gpt-4o";
        }
        String key = configured.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "gpt4o", "gpt 4o", "4o" -> "gpt-4o";
            case "gpt4o mini", "gpt-4o-mini", "4o mini" -> "gpt-4o-mini";
            default -> configured.trim();
        };
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

    private ProviderResult callClaude(String apiKey, String model, String mediaType, String base64) {
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
        return extractClaudeResult(response);
    }

    private ProviderResult callOpenAi(String apiKey, String model, String mediaType, String base64) {
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
        return extractOpenAiResult(response);
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

    private ProviderResult extractClaudeResult(String responseBody) {
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
            TokenUsage usage = parseClaudeUsage(root.get("usage"));
            return new ProviderResult(sb.toString(), usage.inputTokens(), usage.outputTokens());
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("No se pudo interpretar la respuesta de Claude.");
        }
    }

    private ProviderResult extractOpenAiResult(String responseBody) {
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
            TokenUsage usage = parseOpenAiUsage(root.get("usage"));
            return new ProviderResult(String.valueOf(content), usage.inputTokens(), usage.outputTokens());
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("No se pudo interpretar la respuesta de OpenAI.");
        }
    }

    private static TokenUsage parseClaudeUsage(Object usageObj) {
        if (!(usageObj instanceof Map<?, ?> usage)) {
            return TokenUsage.empty();
        }
        return new TokenUsage(asInteger(usage.get("input_tokens")), asInteger(usage.get("output_tokens")));
    }

    private static TokenUsage parseOpenAiUsage(Object usageObj) {
        if (!(usageObj instanceof Map<?, ?> usage)) {
            return TokenUsage.empty();
        }
        return new TokenUsage(asInteger(usage.get("prompt_tokens")), asInteger(usage.get("completion_tokens")));
    }

    private static Integer asInteger(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private ParsedExtraction parseExtraction(String rawText) {
        String json = unwrapJson(rawText);
        try {
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<>() {});
            boolean valido = parseValido(root.get("valido"));
            String motivo = str(root, "motivo", "reason", "mensaje");
            Object filasRaw = root.get("filas");
            List<PlanillaAiExtractDtos.DetalleRow> out = new ArrayList<>();
            if (filasRaw instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> map)) {
                        continue;
                    }
                    PlanillaAiExtractDtos.DetalleRow row = toRow(map);
                    if (hasMeasure(row)) {
                        out.add(row);
                    }
                }
            } else if (valido) {
                throw new BadRequestException("La IA no devolvió el JSON esperado (falta «filas»).");
            }
            if (valido && out.isEmpty()) {
                valido = false;
                if (!StringUtils.hasText(motivo)) {
                    motivo =
                            "La IA no encontró filas de corte (Cant./Largo/Ancho) en la imagen. Pruebe con otra foto más nítida.";
                }
            }
            return new ParsedExtraction(valido, motivo, out);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("JSON IA no parseable: {}", truncate(rawText, 400));
            throw new BadRequestException(
                    "No se pudo interpretar el JSON de la IA. Pruebe otra foto o revise el modelo configurado.");
        }
    }

    private static boolean parseValido(Object raw) {
        if (raw == null) {
            return true; // legacy responses without valido → treat as attempt to extract
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if ("false".equals(s) || "0".equals(s) || "no".equals(s)) {
            return false;
        }
        if ("true".equals(s) || "1".equals(s) || "si".equals(s) || "sí".equals(s)) {
            return true;
        }
        return true;
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

    private record ProviderResult(String text, Integer inputTokens, Integer outputTokens) {}

    private record TokenUsage(Integer inputTokens, Integer outputTokens) {
        static TokenUsage empty() {
            return new TokenUsage(null, null);
        }
    }

    private record ParsedExtraction(boolean valido, String motivo, List<PlanillaAiExtractDtos.DetalleRow> filas) {}
}
