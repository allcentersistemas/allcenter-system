package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.model.Orden;
import com.allcenter.modulesystem.model.OdooWebhookEvent;
import com.allcenter.modulesystem.model.ProyectoOptimizacion;
import com.allcenter.modulesystem.repository.OdooWebhookEventRepository;
import com.allcenter.modulesystem.repository.OrdenRepository;
import com.allcenter.modulesystem.repository.ProyectoRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OdooWebhookService {

    private final OdooWebhookEventRepository repository;
    private final ProyectoRepository proyectoRepository;
    private final OrdenRepository ordenRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OdooWebhookEvent ingest(String tipo, String payload, String contentType, String remoteIp) {
        OdooWebhookEvent event = new OdooWebhookEvent();
        event.setTipo(tipo);
        event.setReceivedAt(Instant.now());
        event.setPayload(payload == null ? "" : payload);
        event.setContentType(trimMax(contentType, 120));
        event.setRemoteIp(trimMax(remoteIp, 128));

        Map<String, Object> fields = parseOdooOrder(payload);
        applyParsedFields(event, fields);

        Optional<ProyectoOptimizacion> matched = matchProject(fields);
        if (matched.isPresent()) {
            ProyectoOptimizacion proyecto = matched.get();
            event.setMatchedProyectoId(proyecto.getId());
            event.setActionTaken("PRUEBA");
            event.setNote(
                    trimMax(
                            "Registrado para prueba. "
                                    + firstNonBlank(event.getOdooName(), event.getOdooDisplayName(), "Orden Odoo")
                                    + " → proyecto #"
                                    + proyecto.getId()
                                    + " "
                                    + nullToEmpty(proyecto.getNombre())
                                    + " (estado no modificado).",
                            500));
        } else {
            event.setActionTaken("SIN_MATCH");
            String oc = firstNonBlank(event.getOdooName(), event.getOdooDisplayName(), null);
            event.setNote(
                    oc == null
                            ? "Orden Odoo recibida. No se identificó un proyecto. Solo se guarda para prueba."
                            : trimMax(
                                    "Orden " + oc + " recibida. No se identificó un proyecto. Solo se guarda para prueba.",
                                    500));
        }
        return repository.save(event);
    }

    @Transactional(readOnly = true)
    public Page<OdooWebhookEvent> list(String tipo, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        PageRequest pr = PageRequest.of(safePage, safeSize);
        if (StringUtils.hasText(tipo)) {
            return repository.findByTipoOrderByReceivedAtDesc(tipo.trim().toUpperCase(Locale.ROOT), pr);
        }
        return repository.findAllByOrderByReceivedAtDesc(pr);
    }

    private Map<String, Object> parseOdooOrder(String payload) {
        if (!StringUtils.hasText(payload)) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
            if (raw == null || raw.isEmpty()) {
                return Map.of();
            }
            Object nested = raw.get("_value");
            if (nested instanceof Map<?, ?> nestedMap) {
                Map<String, Object> merged = new LinkedHashMap<>(raw);
                nestedMap.forEach((k, v) -> merged.putIfAbsent(String.valueOf(k), v));
                return merged;
            }
            return raw;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private void applyParsedFields(OdooWebhookEvent event, Map<String, Object> fields) {
        if (fields.isEmpty()) {
            return;
        }
        event.setOdooRecordId(toLong(firstPresent(fields, "id", "_id")));
        event.setOdooModel(trimMax(asString(firstPresent(fields, "_model", "model")), 80));
        event.setOdooName(trimMax(asString(fields.get("name")), 120));
        event.setOdooDisplayName(trimMax(asString(firstPresent(fields, "display_name", "displayName")), 255));
        Object partner = firstPresent(fields, "partner_id", "partnerId");
        event.setPartnerId(toLong(partner));
        event.setPartnerName(trimMax(partnerName(partner, event.getOdooDisplayName()), 255));
        event.setDateOrder(trimMax(asString(firstPresent(fields, "date_order", "dateOrder")), 40));
        event.setAmountTotal(trimMax(asString(firstPresent(fields, "amount_total", "amountTotal")), 40));
        event.setOdooState(trimMax(asString(fields.get("state")), 40));
    }

    private Optional<ProyectoOptimizacion> matchProject(Map<String, Object> fields) {
        for (String token : matchTokens(fields)) {
            Optional<ProyectoOptimizacion> hit = matchToken(token);
            if (hit.isPresent()) {
                return hit;
            }
        }
        return Optional.empty();
    }

    private List<String> matchTokens(Map<String, Object> fields) {
        java.util.ArrayList<String> tokens = new java.util.ArrayList<>();
        for (String key :
                new String[] {
                    "origin",
                    "partner_ref",
                    "client_order_ref",
                    "x_studio_proyecto",
                    "proyecto_id",
                    "project_id",
                    "name"
                }) {
            String token = nullToEmpty(asString(fields.get(key)));
            if (token.length() >= 2 && !tokens.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private Optional<ProyectoOptimizacion> matchToken(String token) {
        try {
            long id = Long.parseLong(token);
            Optional<ProyectoOptimizacion> byId = proyectoRepository.findById(id);
            if (byId.isPresent()) {
                return byId;
            }
        } catch (NumberFormatException ignored) {
            // continue
        }
        Optional<ProyectoOptimizacion> byRef =
                proyectoRepository.findAllByOrderByFechacreacionDesc().stream()
                        .filter(
                                p ->
                                        token.equalsIgnoreCase(nullToEmpty(p.getReferencia()))
                                                || token.equalsIgnoreCase(nullToEmpty(p.getNombre()))
                                                || (p.getId() != null && token.equals(String.valueOf(p.getId()))))
                        .findFirst();
        if (byRef.isPresent()) {
            return byRef;
        }
        List<Orden> ordenes = ordenRepository.findByCodeOrName(token);
        for (Orden orden : ordenes) {
            if (orden.getProyectoOptimizacionId() != null && orden.getProyectoOptimizacionId().getId() != null) {
                return Optional.of(orden.getProyectoOptimizacionId());
            }
        }
        return Optional.empty();
    }

    private static Object firstPresent(Map<String, Object> fields, String... keys) {
        for (String key : keys) {
            if (fields.containsKey(key) && fields.get(key) != null) {
                return fields.get(key);
            }
        }
        return null;
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof List<?> list && !list.isEmpty()) {
            return toLong(list.getFirst());
        }
        String s = asString(v);
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s.replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String partnerName(Object partnerId, String displayName) {
        if (partnerId instanceof List<?> list && list.size() >= 2 && list.get(1) != null) {
            return String.valueOf(list.get(1)).trim();
        }
        if (displayName != null && displayName.contains(" - ")) {
            return displayName.substring(displayName.indexOf(" - ") + 3).trim();
        }
        return null;
    }

    private static String asString(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof List<?> list && !list.isEmpty()) {
            return asString(list.size() >= 2 ? list.get(1) : list.getFirst());
        }
        String t = String.valueOf(v).trim();
        return t.isEmpty() || "null".equalsIgnoreCase(t) ? null : t;
    }

    private static String firstNonBlank(String a, String b, String fallback) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return fallback;
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v.trim();
    }

    private static String trimMax(String raw, int max) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
