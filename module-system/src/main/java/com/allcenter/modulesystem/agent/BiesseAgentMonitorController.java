package com.allcenter.modulesystem.agent;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

/** Monitor seccionadores + tokens (JWT empleado). El agente Win10 usa {@code /api/biesse/agent}. */
@RestController
@RequestMapping("/api/biesse/monitor")
@RequiredArgsConstructor
public class BiesseAgentMonitorController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern DURATION_SEC =
            Pattern.compile("duraci[oó]n=(\\d+)s", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECCIONADOR =
            Pattern.compile("seccionador=([^\\s]+)", Pattern.CASE_INSENSITIVE);

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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Seccionador no encontrado");
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

    /**
     * Eventos CORTE_INICIO / CORTE_FIN en op_trazabilidad (historial persistido),
     * con seccionador y duración parseados del detalle.
     */
    @GetMapping("/cut-times")
    @PreAuthorize("@portalAuth.canRead()")
    public ResponseEntity<List<Map<String, Object>>> cutTimes(
            @RequestParam(required = false) String op,
            @RequestParam(required = false) Long orderId,
            @RequestParam(defaultValue = "80") int limit) {
        return ResponseEntity.ok(listCutTimeEvents(op, orderId, limit));
    }

    /**
     * Historial agregado por obra (orderid): duración total, seccionadores, inicio/fin.
     * Datos desde CORTE_FIN (duración) y CORTE_INICIO en op_trazabilidad.
     */
    @GetMapping("/cut-times/summary")
    @PreAuthorize("@portalAuth.canRead()")
    public ResponseEntity<List<Map<String, Object>>> cutTimesSummary(
            @RequestParam(required = false) String op,
            @RequestParam(required = false) Long orderId,
            @RequestParam(defaultValue = "40") int limit) {
        int fetch = Math.min(Math.max(limit * 8, 80), 500);
        List<Map<String, Object>> events = listCutTimeEvents(op, orderId, fetch);
        // listTrazabilidad suele venir DESC: invertimos para recorrer cronológico al agregar.
        List<Map<String, Object>> chronological = new ArrayList<>(events);
        chronological.sort(Comparator.comparing(e -> str(e.get("fecha")), Comparator.nullsLast(String::compareTo)));

        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (Map<String, Object> ev : chronological) {
            String key = cutHistoryKey(ev);
            Map<String, Object> agg = byKey.computeIfAbsent(key, k -> newCutSummary(ev));

            String accion = str(ev.get("accion"));
            String upper = accion != null ? accion.toUpperCase(Locale.ROOT) : "";
            Object fecha = ev.get("fecha");
            String sec = str(ev.get("seccionador"));
            if (sec != null && !sec.isBlank()) {
                @SuppressWarnings("unchecked")
                Set<String> secs = (Set<String>) agg.get("_seccionadores");
                secs.add(sec);
            }
            if ("CORTE_INICIO".equals(upper)) {
                if (agg.get("first_start") == null) {
                    agg.put("first_start", fecha);
                }
            } else if ("CORTE_FIN".equals(upper)) {
                Long seconds = ev.get("duration_seconds") instanceof Number n
                        ? n.longValue()
                        : extractDurationSeconds(str(ev.get("detalle")));
                if (seconds != null) {
                    long total = ((Number) agg.get("total_duration_seconds")).longValue() + seconds;
                    agg.put("total_duration_seconds", total);
                    int sessions = ((Number) agg.get("sessions")).intValue() + 1;
                    agg.put("sessions", sessions);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> windows = (List<Map<String, Object>>) agg.get("windows");
                    Map<String, Object> win = new LinkedHashMap<>();
                    win.put("fecha", fecha);
                    win.put("seccionador", sec);
                    win.put("duration_seconds", seconds);
                    win.put("duration_label", formatDuration(seconds));
                    win.put("detalle", ev.get("detalle"));
                    windows.add(win);
                }
                agg.put("last_end", fecha);
                if (agg.get("first_start") == null) {
                    agg.put("first_start", fecha);
                }
            }
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> agg : byKey.values()) {
            @SuppressWarnings("unchecked")
            Set<String> secs = (Set<String>) agg.remove("_seccionadores");
            long total = ((Number) agg.get("total_duration_seconds")).longValue();
            agg.put("total_duration_label", formatDuration(total));
            agg.put("seccionadores", secs != null ? new ArrayList<>(secs) : List.of());
            out.add(agg);
        }
        // Más recientes primero (por last_end / first_start).
        out.sort(
                Comparator.comparing(
                                (Map<String, Object> m) ->
                                        str(m.get("last_end") != null ? m.get("last_end") : m.get("first_start")),
                                Comparator.nullsLast(String::compareTo))
                        .reversed());
        int safeLimit = Math.max(1, Math.min(limit, 200));
        if (out.size() > safeLimit) {
            out = new ArrayList<>(out.subList(0, safeLimit));
        }
        return ResponseEntity.ok(out);
    }

    private List<Map<String, Object>> listCutTimeEvents(String op, Long orderId, int limit) {
        int safe = Math.max(1, Math.min(limit, 400));
        List<Map<String, Object>> rows =
                obrasClient.listTrazabilidad(op, orderId, Math.min(safe * 3, 500));
        List<Map<String, Object>> windows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String accion = str(row.get("accion"));
            if (accion == null) {
                continue;
            }
            String upper = accion.toUpperCase(Locale.ROOT);
            if (!"CORTE_INICIO".equals(upper) && !"CORTE_FIN".equals(upper)) {
                continue;
            }
            String detalle = str(row.get("detalle"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("op_codigo", row.get("op_codigo"));
            item.put("orderid", row.get("orderid"));
            item.put("ordername", row.get("ordername"));
            item.put("accion", accion);
            item.put("fecha", row.get("fecha"));
            item.put("usuario", row.get("usuario"));
            item.put("detalle", detalle);
            item.put("seccionador", extractSeccionador(detalle, str(row.get("usuario"))));
            Long seconds = extractDurationSeconds(detalle);
            item.put("duration_seconds", seconds);
            item.put("duration_label", seconds != null ? formatDuration(seconds) : null);
            windows.add(item);
            if (windows.size() >= safe) {
                break;
            }
        }
        return windows;
    }

    private static String cutHistoryKey(Map<String, Object> ev) {
        Object oid = ev.get("orderid");
        if (oid != null && !String.valueOf(oid).isBlank() && !"null".equals(String.valueOf(oid))) {
            return "o:" + oid;
        }
        String op = str(ev.get("op_codigo"));
        if (op != null && !op.isBlank()) {
            return "op:" + op;
        }
        String name = str(ev.get("ordername"));
        return "n:" + (name != null ? name : "unknown");
    }

    private static Map<String, Object> newCutSummary(Map<String, Object> ev) {
        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("orderid", ev.get("orderid"));
        agg.put("ordername", ev.get("ordername"));
        agg.put("op_codigo", ev.get("op_codigo"));
        agg.put("total_duration_seconds", 0L);
        agg.put("sessions", 0);
        agg.put("first_start", null);
        agg.put("last_end", null);
        agg.put("_seccionadores", new LinkedHashSet<String>());
        agg.put("windows", new ArrayList<Map<String, Object>>());
        return agg;
    }

    private static String extractSeccionador(String detalle, String usuario) {
        if (detalle != null) {
            Matcher m = SECCIONADOR.matcher(detalle);
            if (m.find()) {
                return m.group(1);
            }
            Matcher legacy = Pattern.compile("m[aá]quina=([^\\s]+)", Pattern.CASE_INSENSITIVE).matcher(detalle);
            if (legacy.find()) {
                return legacy.group(1);
            }
        }
        if (usuario != null && usuario.regionMatches(true, 0, "AGENTE:", 0, 7)) {
            return usuario.substring(7).trim();
        }
        return usuario;
    }

    private static Long extractDurationSeconds(String detalle) {
        if (detalle == null || detalle.isBlank()) {
            return null;
        }
        Matcher m = DURATION_SEC.matcher(detalle);
        if (!m.find()) {
            return null;
        }
        try {
            return Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) {
            return h + "h " + m + "m " + s + "s";
        }
        if (m > 0) {
            return m + "m " + s + "s";
        }
        return s + "s";
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String generateToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
