package com.allcenter.modulesystem.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/** Cliente HTTP hacia module-biesse (obras/XML) con token interno. */
@Component
public class BiesseObrasClient {

    private static final Logger log = LoggerFactory.getLogger(BiesseObrasClient.class);

    private final RestTemplate restTemplate;
    private final String biesseBaseUrl;
    private final String internalToken;

    public BiesseObrasClient(
            @Value("${app.biesse.base-url:http://localhost:8086}") String biesseBaseUrl,
            @Value("${app.biesse.internal-token:dev-biesse-internal}") String internalToken) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(8000);
        this.restTemplate = new RestTemplate(factory);
        this.biesseBaseUrl =
                biesseBaseUrl == null ? "http://localhost:8086" : biesseBaseUrl.replaceAll("/+$", "");
        this.internalToken = internalToken != null ? internalToken.trim() : "";
    }

    public Map<String, Object> findOrderById(long orderId) {
        try {
            String url = biesseBaseUrl + "/api/biesse/scan/integration/orders/" + orderId;
            ResponseEntity<Map<String, Object>> res =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            new HttpEntity<>(headers()),
                            new ParameterizedTypeReference<>() {});
            return res.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.warn("biesse findOrderById({}): {}", orderId, e.getMessage());
            return null;
        }
    }

    public Map<String, Object> listOrders(String q, int limit, int offset) {
        try {
            UriComponentsBuilder b =
                    UriComponentsBuilder.fromUriString(biesseBaseUrl + "/api/biesse/scan/integration/orders")
                            .queryParam("limit", limit)
                            .queryParam("offset", offset);
            if (q != null && !q.isBlank()) {
                b.queryParam("q", q.trim());
            }
            ResponseEntity<Map<String, Object>> res =
                    restTemplate.exchange(
                            b.toUriString(),
                            HttpMethod.GET,
                            new HttpEntity<>(headers()),
                            new ParameterizedTypeReference<>() {});
            return res.getBody() != null ? res.getBody() : Map.of("items", List.of(), "totalCount", 0);
        } catch (Exception e) {
            log.warn("biesse listOrders: {}", e.getMessage());
            return Map.of("items", List.of(), "totalCount", 0);
        }
    }

    public Map<String, Object> getOpSummary(String opCodigo) {
        if (opCodigo == null || opCodigo.isBlank()) {
            return null;
        }
        try {
            String encoded =
                    org.springframework.web.util.UriUtils.encodePathSegment(
                            opCodigo.trim(), java.nio.charset.StandardCharsets.UTF_8);
            String url = biesseBaseUrl + "/api/biesse/scan/integration/ops/" + encoded;
            ResponseEntity<Map<String, Object>> res =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            new HttpEntity<>(headers()),
                            new ParameterizedTypeReference<>() {});
            return res.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.warn("biesse getOpSummary('{}'): {}", opCodigo, e.getMessage());
            return null;
        }
    }

    public Map<String, Object> findOrderForJob(String jobName) {
        if (jobName == null || jobName.isBlank()) {
            return null;
        }
        try {
            String url =
                    UriComponentsBuilder.fromUriString(biesseBaseUrl + "/api/biesse/scan/integration/orders/by-job")
                            .queryParam("jobName", jobName)
                            .toUriString();
            ResponseEntity<Map<String, Object>> res =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            new HttpEntity<>(headers()),
                            new ParameterizedTypeReference<>() {});
            Map<String, Object> body = res.getBody();
            if (body == null) {
                return null;
            }
            if (Boolean.TRUE.equals(body.get("ambiguous"))) {
                return null;
            }
            Object order = body.get("order");
            if (order instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                return typed;
            }
            return null;
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.warn("biesse findOrderForJob('{}'): {}", jobName, e.getMessage());
            return null;
        }
    }

    public Map<String, Object> resolveOrderForJob(String jobName) {
        if (jobName == null || jobName.isBlank()) {
            return emptyResolve();
        }
        try {
            String url =
                    UriComponentsBuilder.fromUriString(biesseBaseUrl + "/api/biesse/scan/integration/orders/by-job")
                            .queryParam("jobName", jobName)
                            .toUriString();
            ResponseEntity<Map<String, Object>> res =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            new HttpEntity<>(headers()),
                            new ParameterizedTypeReference<>() {});
            Map<String, Object> body = res.getBody();
            Map<String, Object> out = body != null ? new HashMap<>(body) : emptyResolve();
            out.putIfAbsent("biesseBaseUrl", biesseBaseUrl);
            return out;
        } catch (HttpClientErrorException.NotFound e) {
            log.warn(
                    "biesse resolveOrderForJob('{}'): HTTP 404 from {} — {}",
                    jobName,
                    biesseBaseUrl,
                    trimBody(e.getResponseBodyAsString()));
            Map<String, Object> m = emptyResolve();
            m.put("biesseStatus", 404);
            m.put("biesseBaseUrl", biesseBaseUrl);
            m.put("bridgeBody", trimBody(e.getResponseBodyAsString()));
            return m;
        } catch (HttpClientErrorException e) {
            log.warn(
                    "biesse resolveOrderForJob('{}'): HTTP {} {}",
                    jobName,
                    e.getStatusCode().value(),
                    e.getResponseBodyAsString());
            Map<String, Object> m = emptyResolve();
            m.put("bridgeError", e.getStatusCode().value() + " " + e.getStatusText());
            m.put("bridgeBody", trimBody(e.getResponseBodyAsString()));
            m.put("biesseBaseUrl", biesseBaseUrl);
            return m;
        } catch (Exception e) {
            log.warn("biesse resolveOrderForJob('{}'): {}", jobName, e.getMessage());
            Map<String, Object> m = emptyResolve();
            m.put("bridgeError", e.getMessage());
            m.put("biesseBaseUrl", biesseBaseUrl);
            return m;
        }
    }

    /** Map.of no admite null → NPE y el agente veía 500 en order-manifest. */
    private static Map<String, Object> emptyResolve() {
        Map<String, Object> m = new HashMap<>();
        m.put("ambiguous", false);
        m.put("order", null);
        m.put("candidates", List.of());
        return m;
    }

    private static String trimBody(String body) {
        if (body == null) {
            return null;
        }
        String t = body.trim();
        return t.length() > 400 ? t.substring(0, 400) + "…" : t;
    }

    public ManifestFetch orderManifestFetch(String jobName) {
        return orderManifestFetch(jobName, null);
    }

    public ManifestFetch orderManifestFetch(String jobName, Long orderId) {
        if ((jobName == null || jobName.isBlank()) && (orderId == null || orderId <= 0)) {
            return ManifestFetch.missing("job u orderId requerido");
        }
        try {
            UriComponentsBuilder b =
                    UriComponentsBuilder.fromUriString(
                            biesseBaseUrl + "/api/biesse/scan/integration/orders/manifest");
            if (orderId != null && orderId > 0) {
                b.queryParam("orderId", orderId);
            }
            if (jobName != null && !jobName.isBlank()) {
                b.queryParam("jobName", jobName);
            }
            ResponseEntity<Map<String, Object>> res =
                    restTemplate.exchange(
                            b.toUriString(),
                            HttpMethod.GET,
                            new HttpEntity<>(headers()),
                            new ParameterizedTypeReference<>() {});
            Map<String, Object> body = res.getBody();
            if (body == null || body.isEmpty()) {
                return ManifestFetch.missing("respuesta vacía de module-biesse");
            }
            return ManifestFetch.ok(body);
        } catch (HttpClientErrorException.NotFound e) {
            return ManifestFetch.missing(
                    "module-biesse: obra no encontrada"
                            + (orderId != null ? " id=" + orderId : "")
                            + (jobName != null ? " job=" + jobName : "")
                            + (e.getResponseBodyAsString() != null
                                    ? " — " + trimBody(e.getResponseBodyAsString())
                                    : ""));
        } catch (HttpClientErrorException.Conflict e) {
            return ManifestFetch.ambiguous(trimBody(e.getResponseBodyAsString()));
        } catch (HttpClientErrorException e) {
            log.warn(
                    "biesse orderManifest(job='{}', id={}): HTTP {} {}",
                    jobName,
                    orderId,
                    e.getStatusCode().value(),
                    e.getResponseBodyAsString());
            return ManifestFetch.bridgeFailure(
                    e.getStatusCode().value() + " " + e.getStatusText(),
                    trimBody(e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.warn("biesse orderManifest(job='{}', id={}): {}", jobName, orderId, e.getMessage());
            return ManifestFetch.bridgeFailure(e.getMessage(), null);
        }
    }

    public Map<String, Object> orderManifest(String jobName) {
        ManifestFetch fetch = orderManifestFetch(jobName);
        return fetch.body();
    }

    public record ManifestFetch(
            Map<String, Object> body, String kind, String message, String detail) {
        static ManifestFetch ok(Map<String, Object> body) {
            return new ManifestFetch(body, "ok", null, null);
        }

        static ManifestFetch missing(String message) {
            return new ManifestFetch(null, "missing", message, null);
        }

        static ManifestFetch ambiguous(String detail) {
            return new ManifestFetch(null, "ambiguous", "Obra ambigua", detail);
        }

        static ManifestFetch bridgeFailure(String message, String detail) {
            return new ManifestFetch(null, "bridge", message, detail);
        }
    }

    public String labelZpl(
            String jobName, String osiPart, int pieceNumber, String unitCode, String machineName) {
        if (jobName == null || jobName.isBlank() || osiPart == null || osiPart.isBlank()) {
            return null;
        }
        try {
            UriComponentsBuilder b =
                    UriComponentsBuilder.fromUriString(
                                    biesseBaseUrl + "/api/biesse/scan/integration/labels/zpl")
                            .queryParam("jobName", jobName.trim())
                            .queryParam("osiPart", osiPart.trim())
                            .queryParam("pieceNumber", pieceNumber)
                            .queryParam("unitCode", unitCode != null ? unitCode : "");
            if (machineName != null && !machineName.isBlank()) {
                b.queryParam("machineName", machineName.trim());
            }
            ResponseEntity<Map<String, Object>> res =
                    restTemplate.exchange(
                            b.toUriString(),
                            HttpMethod.GET,
                            new HttpEntity<>(headers()),
                            new ParameterizedTypeReference<>() {});
            Map<String, Object> body = res.getBody();
            return body != null ? str(body.get("zpl")) : null;
        } catch (Exception e) {
            log.warn("biesse labelZpl('{}' {}): {}", jobName, osiPart, e.getMessage());
            return null;
        }
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public boolean markOrderProduccion(long orderId) {
        try {
            String url = biesseBaseUrl + "/api/biesse/scan/integration/orders/" + orderId + "/produccion";
            ResponseEntity<Map<String, Object>> res =
                    restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            new HttpEntity<>(Map.of(), headers()),
                            new ParameterizedTypeReference<>() {});
            Map<String, Object> body = res.getBody();
            return body != null && Boolean.TRUE.equals(body.get("changed"));
        } catch (Exception e) {
            log.warn("biesse markProduccion({}): {}", orderId, e.getMessage());
            return false;
        }
    }

    public List<Map<String, Object>> listSeguimientoObras(int limit) {
        return listSeguimientoObras(limit, null);
    }

    public List<Map<String, Object>> listSeguimientoObras(int limit, String since) {
        try {
            UriComponentsBuilder b =
                    UriComponentsBuilder.fromUriString(
                                    biesseBaseUrl + "/api/biesse/scan/integration/seguimiento")
                            .queryParam("limit", Math.max(1, Math.min(limit, 500)));
            if (since != null && !since.isBlank()) {
                b.queryParam("since", since.trim());
            }
            ResponseEntity<List<Map<String, Object>>> res =
                    restTemplate.exchange(
                            b.toUriString(),
                            HttpMethod.GET,
                            new HttpEntity<>(headers()),
                            new ParameterizedTypeReference<>() {});
            return res.getBody() != null ? res.getBody() : List.of();
        } catch (Exception e) {
            log.warn("biesse listSeguimientoObras: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> markOrderEntregado(long orderId, String usuario) {
        try {
            UriComponentsBuilder b =
                    UriComponentsBuilder.fromUriString(
                                    biesseBaseUrl
                                            + "/api/biesse/scan/integration/orders/"
                                            + orderId
                                            + "/entregado");
            if (usuario != null && !usuario.isBlank()) {
                b.queryParam("usuario", usuario.trim());
            }
            ResponseEntity<Map<String, Object>> res =
                    restTemplate.exchange(
                            b.toUriString(),
                            HttpMethod.POST,
                            new HttpEntity<>(Map.of(), headers()),
                            new ParameterizedTypeReference<>() {});
            return res.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.warn("biesse markEntregado({}): {}", orderId, e.getMessage());
            return null;
        }
    }

    public Map<String, Object> markOrderEntregadoByRef(
            String orderName, String bookingCode, String usuario) {
        try {
            Map<String, Object> body = new HashMap<>();
            if (orderName != null && !orderName.isBlank()) {
                body.put("orderName", orderName.trim());
            }
            if (bookingCode != null && !bookingCode.isBlank()) {
                body.put("bookingCode", bookingCode.trim());
            }
            if (usuario != null && !usuario.isBlank()) {
                body.put("usuario", usuario.trim());
            }
            ResponseEntity<Map<String, Object>> res =
                    restTemplate.exchange(
                            biesseBaseUrl + "/api/biesse/scan/integration/orders/entregado",
                            HttpMethod.POST,
                            new HttpEntity<>(body, headers()),
                            new ParameterizedTypeReference<>() {});
            return res.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.warn("biesse markEntregadoByRef: {}", e.getMessage());
            return null;
        }
    }

    public void registrarTrazabilidad(
            String opCodigo,
            Long orderId,
            String orderName,
            String estado,
            String accion,
            String detalle,
            int piezas,
            int partes,
            String usuario) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("opCodigo", opCodigo);
            body.put("orderId", orderId);
            body.put("orderName", orderName);
            body.put("estado", estado);
            body.put("accion", accion);
            body.put("detalle", detalle);
            body.put("piezas", piezas);
            body.put("partes", partes);
            body.put("usuario", usuario);
            restTemplate.exchange(
                    biesseBaseUrl + "/api/biesse/scan/integration/trazabilidad",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers()),
                    Void.class);
        } catch (Exception e) {
            log.warn("biesse trazabilidad order={}: {}", orderId, e.getMessage());
        }
    }

    public Map<String, Object> partForOsi(long orderId, String osiPart, String machineName) {
        return partForOsi(orderId, osiPart, machineName, null, null, true);
    }

    public Map<String, Object> partForOsi(
            long orderId, String osiPart, String machineName, Integer pieceNumber, String unitCode) {
        return partForOsi(orderId, osiPart, machineName, pieceNumber, unitCode, true);
    }

    public Map<String, Object> partForOsi(
            long orderId,
            String osiPart,
            String machineName,
            Integer pieceNumber,
            String unitCode,
            boolean markCortada) {
        try {
            UriComponentsBuilder b =
                    UriComponentsBuilder.fromUriString(biesseBaseUrl + "/api/biesse/scan/integration/parts/for-osi")
                            .queryParam("orderId", orderId)
                            .queryParam("osiPart", osiPart)
                            .queryParam("machineName", machineName != null ? machineName : "")
                            .queryParam("markCortada", markCortada);
            if (pieceNumber != null && pieceNumber > 0) {
                b.queryParam("pieceNumber", pieceNumber);
            }
            if (unitCode != null && !unitCode.isBlank()) {
                b.queryParam("unitCode", unitCode.trim());
            }
            String url = b.toUriString();
            ResponseEntity<Map<String, Object>> res =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            new HttpEntity<>(headers()),
                            new ParameterizedTypeReference<>() {});
            return res.getBody();
        } catch (Exception e) {
            log.warn("biesse partForOsi({}, {}): {}", orderId, osiPart, e.getMessage());
            return null;
        }
    }

    /** Marca pieza cortada (POST) — respaldo si GET for-osi falló o no pintó. */
    public Map<String, Object> markCortada(
            long orderId, String osiPart, String machineName, Integer pieceNumber) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("orderId", orderId);
            body.put("osiPart", osiPart != null ? osiPart : "");
            if (machineName != null && !machineName.isBlank()) {
                body.put("machineName", machineName.trim());
            }
            if (pieceNumber != null && pieceNumber > 0) {
                body.put("pieceNumber", pieceNumber);
            }
            String url = biesseBaseUrl + "/api/biesse/scan/integration/parts/mark-cortada";
            ResponseEntity<Map<String, Object>> res =
                    restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            new HttpEntity<>(body, headers()),
                            new ParameterizedTypeReference<>() {});
            return res.getBody();
        } catch (Exception e) {
            log.warn("biesse markCortada({}, {}): {}", orderId, osiPart, e.getMessage());
            return null;
        }
    }

    public List<Map<String, Object>> listTrazabilidad(String op, Long orderId, int limit) {
        return listTrazabilidad(op, orderId, limit, false);
    }

    public List<Map<String, Object>> listTrazabilidad(
            String op, Long orderId, int limit, boolean soloCorte) {
        try {
            UriComponentsBuilder b =
                    UriComponentsBuilder.fromUriString(biesseBaseUrl + "/api/biesse/scan/integration/trazabilidad")
                            .queryParam("limit", limit);
            if (op != null && !op.isBlank()) {
                b.queryParam("op", op);
            }
            if (orderId != null) {
                b.queryParam("orderId", orderId);
            }
            if (soloCorte) {
                b.queryParam("soloCorte", true);
            }
            ResponseEntity<List<Map<String, Object>>> res =
                    restTemplate.exchange(
                            b.toUriString(),
                            HttpMethod.GET,
                            new HttpEntity<>(headers()),
                            new ParameterizedTypeReference<>() {});
            return res.getBody() != null ? res.getBody() : List.of();
        } catch (Exception e) {
            log.warn("biesse listTrazabilidad: {}", e.getMessage());
            return List.of();
        }
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (!internalToken.isBlank()) {
            h.set("X-Internal-Token", internalToken);
        }
        return h;
    }
}
