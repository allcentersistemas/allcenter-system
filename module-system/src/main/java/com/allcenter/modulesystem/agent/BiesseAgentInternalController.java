package com.allcenter.modulesystem.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Integración module-biesse → cortes del agente (sin JWT de portal). */
@RestController
@RequestMapping("/api/biesse/monitor/internal")
@RequiredArgsConstructor
public class BiesseAgentInternalController {

    private final BiesseAgentRepository agentRepository;
    private final BiesseAgentSchemaAligner schemaAligner;

    @Value("${app.biesse.internal-token:dev-biesse-internal}")
    private String internalToken;

    @GetMapping("/cut-pieces")
    public ResponseEntity<List<Map<String, Object>>> cutPiecesByOrder(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam long orderId,
            @RequestParam(defaultValue = "500") int limit) {
        requireInternal(token);
        schemaAligner.ensureReady();
        return ResponseEntity.ok(agentRepository.listCutPieces(orderId, limit));
    }

    /**
     * Máquinas cuyo job/orden actual coincide con la obra — para pintar cortes desde {@code last_part}.
     */
    @GetMapping("/machines-for-order")
    public ResponseEntity<List<Map<String, Object>>> machinesForOrder(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam long orderId,
            @RequestParam(required = false) String orderName) {
        requireInternal(token);
        schemaAligner.ensureReady();
        return ResponseEntity.ok(agentRepository.listMachinesForOrder(orderId, orderName));
    }

    private void requireInternal(String token) {
        String expected = internalToken != null ? internalToken.trim() : "";
        String got = token != null ? token.trim() : "";
        boolean matches =
                !expected.isBlank()
                        && MessageDigest.isEqual(
                                expected.getBytes(StandardCharsets.UTF_8), got.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token interno inválido");
        }
    }
}
