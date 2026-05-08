package com.allcenter.modulebiesse.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthGatewayService {

    private final RestTemplate restTemplate;
    private final String meEndpoint;

    public AuthGatewayService(
            @Value("${app.auth.me-endpoint:http://localhost:8081/api/auth/me}") String meEndpoint) {
        this.restTemplate = new RestTemplate();
        this.meEndpoint = meEndpoint;
    }

    public Long resolveEmployeeId(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Authorization header is required");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response =
                    restTemplate.exchange(
                            meEndpoint,
                            HttpMethod.GET,
                            entity,
                            new ParameterizedTypeReference<>() {});
            Map<String, Object> profile = response.getBody();

            if (profile == null || profile.get("id") == null) {
                throw new ResponseStatusException(UNAUTHORIZED, "Unable to resolve employee from token");
            }

            Object id = profile.get("id");
            if (id instanceof Number n) {
                return n.longValue();
            }
            return Long.parseLong(String.valueOf(id));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    UNAUTHORIZED, "Token is invalid or auth service is unavailable");
        }
    }
}
