package com.allcenter.modulesystem.agent;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Monitor CNC + tokens (JWT empleado). El agente Win10 usa {@code /api/biesse/agent}. */
@RestController
@RequestMapping("/api/biesse/monitor")
@RequiredArgsConstructor
public class BiesseAgentMonitorController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final BiesseAgentRepository agentRepository;
    private final BiesseAgentSchemaAligner schemaAligner;
    private final BiesseObrasClient obrasClient;

    public record CreateMachineRequest(String machineName, String plantName) {}

    @GetMapping("/machines")
    @PreAuthorize("@portalAuth.canRead()")
    public ResponseEntity<List<Map<String, Object>>> machines() {
        schemaAligner.ensureReady();
        return ResponseEntity.ok(agentRepository.listMachines());
    }

    @PostMapping("/machines")
    @PreAuthorize("@portalAuth.canUpdate()")
    public ResponseEntity<Map<String, Object>> createMachine(@RequestBody CreateMachineRequest body) {
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
                        + "http://IP:8080 y este token en X-Agent-Token / config.");
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    @PostMapping("/machines/{machineId}/rotate-token")
    @PreAuthorize("@portalAuth.canUpdate()")
    public ResponseEntity<Map<String, Object>> rotateToken(@PathVariable int machineId) {
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
    @PreAuthorize("@portalAuth.canRead()")
    public ResponseEntity<List<Map<String, Object>>> events(
            @RequestParam(defaultValue = "80") int limit) {
        schemaAligner.ensureReady();
        return ResponseEntity.ok(agentRepository.listRecentEvents(limit));
    }

    @GetMapping("/cut-pieces")
    @PreAuthorize("@portalAuth.canRead()")
    public ResponseEntity<List<Map<String, Object>>> cutPieces(
            @RequestParam(defaultValue = "40") int limit) {
        schemaAligner.ensureReady();
        return ResponseEntity.ok(agentRepository.listRecentCutPieces(limit));
    }

    @GetMapping("/trazabilidad")
    @PreAuthorize("@portalAuth.canRead()")
    public ResponseEntity<List<Map<String, Object>>> trazabilidad(
            @RequestParam(required = false) String op,
            @RequestParam(required = false) Long orderId,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(obrasClient.listTrazabilidad(op, orderId, limit));
    }

    private static String generateToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
