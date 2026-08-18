package com.allcenter.modulebiesse.service;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Avisa a module-system del avance de escaneo Android (despacho / listo para entregar).
 * Un fallo aquí no debe revertir el escaneo.
 */
@Service
@Slf4j
public class SystemFulfillmentClient {

    private final RestTemplate restTemplate;
    private final String systemBaseUrl;

    public SystemFulfillmentClient(
            @Value("${app.system.base-url:http://localhost:8080}") String systemBaseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(4000);
        this.restTemplate = new RestTemplate(factory);
        this.systemBaseUrl = systemBaseUrl == null ? "http://localhost:8080" : systemBaseUrl.replaceAll("/+$", "");
    }

    public void notifyAndroidScan(String authorization, BiesseScanService.AndroidScanNotify notify) {
        if (notify == null) {
            return;
        }
        String orderName = blankToNull(notify.orderName());
        String bookingCode = blankToNull(notify.bookingCode());
        if (orderName == null && bookingCode == null) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            if (authorization != null && !authorization.isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, authorization);
            }
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("orderName", orderName);
            body.put("bookingCode", bookingCode);
            body.put("orderComplete", notify.complete());
            restTemplate.postForEntity(
                    systemBaseUrl + "/api/order/fulfillment/android-scan",
                    new HttpEntity<>(body, headers),
                    Void.class);
        } catch (Exception ex) {
            log.warn("No se pudo notificar seguimiento post-venta: {}", ex.getMessage());
        }
    }

    private static String blankToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() || "null".equalsIgnoreCase(t) ? null : t;
    }
}
