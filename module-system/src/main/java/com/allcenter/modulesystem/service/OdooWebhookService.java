package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.model.OdooWebhookEvent;
import com.allcenter.modulesystem.model.ProyectoOptimizacion;
import com.allcenter.modulesystem.repository.OdooWebhookEventRepository;
import com.allcenter.modulesystem.repository.ProyectoRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OdooWebhookService {

    private static final Pattern ID_PATTERN =
            Pattern.compile("\"(?:proyecto[_-]?id|project[_-]?id|x_proyecto_id)\"\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXT_PATTERN =
            Pattern.compile(
                    "\"(?:referencia|origin|partner_ref|x_studio_proyecto|nombre|name|client_order_ref)\"\\s*:\\s*\"([^\"]{2,180})\"",
                    Pattern.CASE_INSENSITIVE);

    private final OdooWebhookEventRepository repository;
    private final ProyectoRepository proyectoRepository;

    @Transactional
    public OdooWebhookEvent ingest(String tipo, String payload, String contentType, String remoteIp) {
        OdooWebhookEvent event = new OdooWebhookEvent();
        event.setTipo(tipo);
        event.setReceivedAt(Instant.now());
        event.setPayload(payload == null ? "" : payload);
        event.setContentType(trimMax(contentType, 120));
        event.setRemoteIp(trimMax(remoteIp, 128));

        Optional<ProyectoOptimizacion> matched = matchProject(payload);
        if (matched.isPresent()) {
            ProyectoOptimizacion proyecto = matched.get();
            event.setMatchedProyectoId(proyecto.getId());
            event.setActionTaken("PRUEBA");
            event.setNote(
                    "Registrado para prueba. Proyecto #"
                            + proyecto.getId()
                            + " "
                            + nullToEmpty(proyecto.getNombre())
                            + " (estado no modificado).");
        } else {
            event.setActionTaken("SIN_MATCH");
            event.setNote("No se identificó un proyecto a partir del payload. Solo se guarda para prueba.");
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

    private Optional<ProyectoOptimizacion> matchProject(String payload) {
        if (!StringUtils.hasText(payload)) {
            return Optional.empty();
        }
        Matcher idMatcher = ID_PATTERN.matcher(payload);
        if (idMatcher.find()) {
            try {
                long id = Long.parseLong(idMatcher.group(1));
                Optional<ProyectoOptimizacion> byId = proyectoRepository.findById(id);
                if (byId.isPresent()) {
                    return byId;
                }
            } catch (NumberFormatException ignored) {
                // continue
            }
        }
        Matcher textMatcher = TEXT_PATTERN.matcher(payload);
        while (textMatcher.find()) {
            String token = textMatcher.group(1).trim();
            if (token.length() < 2) {
                continue;
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
        }
        return Optional.empty();
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
