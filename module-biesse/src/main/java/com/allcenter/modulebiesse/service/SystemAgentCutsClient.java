package com.allcenter.modulebiesse.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/** Cortes del agente (module-system) para sincronizar {@code piezas.cortada}. */
@Service
@Slf4j
public class SystemAgentCutsClient {

    private final RestTemplate restTemplate;
    private final String systemBaseUrl;
    private final String internalToken;

    public SystemAgentCutsClient(
            @Value("${app.system.base-url:http://localhost:8080}") String systemBaseUrl,
            @Value("${app.biesse.internal-token:dev-biesse-internal}") String internalToken) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
        this.systemBaseUrl =
                systemBaseUrl == null ? "http://localhost:8080" : systemBaseUrl.replaceAll("/+$", "");
        this.internalToken = internalToken != null ? internalToken.trim() : "";
    }

    public List<Map<String, Object>> listCutPieces(long orderId, int limit) {
        if (internalToken.isBlank()) {
            return List.of();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", internalToken);
            String url =
                    systemBaseUrl
                            + "/api/biesse/monitor/internal/cut-pieces?orderId="
                            + orderId
                            + "&limit="
                            + Math.max(1, Math.min(limit, 500));
            ResponseEntity<List<Map<String, Object>>> res =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            new ParameterizedTypeReference<>() {});
            return res.getBody() != null ? res.getBody() : List.of();
        } catch (Exception ex) {
            log.warn("No se pudieron leer cortes del agente para orderId={}: {}", orderId, ex.getMessage());
            return Collections.emptyList();
        }
    }
}
