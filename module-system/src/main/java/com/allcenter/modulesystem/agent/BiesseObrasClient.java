package com.allcenter.modulesystem.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class BiesseObrasClient {

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
            return res.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.warn("biesse findOrderForJob('{}'): {}", jobName, e.getMessage());
            return null;
        }
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
        try {
            String url =
                    UriComponentsBuilder.fromUriString(biesseBaseUrl + "/api/biesse/scan/integration/parts/for-osi")
                            .queryParam("orderId", orderId)
                            .queryParam("osiPart", osiPart)
                            .queryParam("machineName", machineName != null ? machineName : "")
                            .toUriString();
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

    public List<Map<String, Object>> listTrazabilidad(String op, Long orderId, int limit) {
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
