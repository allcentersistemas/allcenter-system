package com.allcenter.modulebiesse.controller;

import com.allcenter.modulebiesse.agent.BiesseAgentRepository;
import com.allcenter.modulebiesse.agent.BiesseAgentSchemaAligner;
import com.allcenter.security.BiessePortalRoleAuthorization;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Lectura JWT del monitor CNC + alta de tokens (no usa X-Agent-Token). */
@RestController
@RequestMapping("/api/biesse/scan/agent")
@RequiredArgsConstructor
public class BiesseAgentMonitorController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final BiesseAgentRepository agentRepository;
    private final BiesseAgentSchemaAligner schemaAligner;
    private final BiessePortalRoleAuthorization portalAuth;

    public record CreateMachineRequest(String machineName, String plantName) {}

    @GetMapping("/machines")
    public ResponseEntity<List<Map<String, Object>>> machines(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        portalAuth.requireRead(authorization);
        schemaAligner.ensureReady();
        return ResponseEntity.ok(agentRepository.listMachines());
    }

    @PostMapping("/machines")
    public ResponseEntity<Map<String, Object>> createMachine(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody CreateMachineRequest body) {
        portalAuth.requireAdminOps(authorization);
        schemaAligner.ensureReady();
        String name =
                body != null && body.machineName() != null && !body.machineName().isBlank()
                        ? body.machineName().trim()
                        : "BIESSE-OSI";
        String plant =
                body != null && body.plantName() != null && !body.plantName().isBlank()
                        ? body.plantName().trim()
                        : null;
        String rawToken = generateToken();
        String hash = BiesseAgentSchemaAligner.sha256Hex(rawToken);
        Map<String, Object> machine = agentRepository.createMachine(name, plant, hash);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("machine", machine);
        out.put("token", rawToken);
        out.put(
                "message",
                "Guarde el token ahora: no se vuelve a mostrar. En el agente Win10 use URL "
                        + "http://IP:8086 y este token en X-Agent-Token / config.");
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    @PostMapping("/machines/{machineId}/rotate-token")
    public ResponseEntity<Map<String, Object>> rotateToken(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable int machineId) {
        portalAuth.requireAdminOps(authorization);
        schemaAligner.ensureReady();
        String rawToken = generateToken();
        String hash = BiesseAgentSchemaAligner.sha256Hex(rawToken);
        if (!agentRepository.rotateToken(machineId, hash)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Máquina no encontrada");
        }
        Map<String, Object> machine = agentRepository.findMachineById(machineId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("machine", machine);
        out.put("token", rawToken);
        out.put("message", "Token rotado. Actualice config.json del agente con el nuevo valor.");
        return ResponseEntity.ok(out);
    }

    @GetMapping("/events")
    public ResponseEntity<List<Map<String, Object>>> events(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(defaultValue = "80") int limit) {
        portalAuth.requireRead(authorization);
        schemaAligner.ensureReady();
        return ResponseEntity.ok(agentRepository.listRecentEvents(limit));
    }

    @GetMapping("/cut-pieces")
    public ResponseEntity<List<Map<String, Object>>> cutPieces(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(defaultValue = "40") int limit) {
        portalAuth.requireRead(authorization);
        schemaAligner.ensureReady();
        return ResponseEntity.ok(agentRepository.listRecentCutPieces(limit));
    }

    @GetMapping("/trazabilidad")
    public ResponseEntity<List<Map<String, Object>>> trazabilidad(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(required = false) String op,
            @RequestParam(required = false) Long orderId,
            @RequestParam(defaultValue = "100") int limit) {
        portalAuth.requireRead(authorization);
        schemaAligner.ensureReady();
        return ResponseEntity.ok(agentRepository.listTrazabilidad(op, orderId, limit));
    }

    private static String generateToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
