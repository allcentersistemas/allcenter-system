package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.model.Guia;
import com.allcenter.modulesystem.model.RmRegistroEntrada;
import com.allcenter.modulesystem.model.RmRegistroSalida;
import com.allcenter.modulesystem.repository.GuiaRepository;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RmRegistroDocumentResolver {

    private final GuiaRepository guiaRepository;

    public record Resolved(String numeroGuia, String ocNumero) {}

    /**
     * Resuelve guía/OC del propio registro ({@code numero_guia} / {@code oc_numero}).
     * Solo si faltan, completa desde la guía de inventario vinculada ({@code guia_inventario_id}).
     * No usa {@code guia_numero} del vehículo RM — cada entrada/salida tiene su documento.
     */
    @Transactional(readOnly = true)
    public Resolved resolve(Long guiaInventarioId, String guiaStored, String ocStored) {
        String guia = trimOrNull(guiaStored);
        String oc = trimOrNull(ocStored);
        if (guiaInventarioId != null && guiaInventarioId > 0) {
            Optional<Guia> linked = guiaRepository.findById(guiaInventarioId);
            if (linked.isPresent()) {
                if (guia == null) {
                    guia = trimOrNull(linked.get().getNumeroGuia());
                }
                if (oc == null) {
                    oc = trimOrNull(linked.get().getOrdenCompra());
                }
            }
        }
        return new Resolved(guia != null ? guia : "", oc != null ? oc : "");
    }

    @Transactional(readOnly = true)
    public Resolved forEntrada(RmRegistroEntrada entrada) {
        return resolve(entrada.getGuiaInventarioId(), entrada.getNumeroGuia(), entrada.getOcNumero());
    }

    @Transactional(readOnly = true)
    public Resolved forSalida(RmRegistroSalida salida) {
        return resolve(salida.getGuiaInventarioId(), salida.getNumeroGuia(), salida.getOcNumero());
    }

    public static String normalizeSearchTerm(String q) {
        if (q == null) {
            return null;
        }
        String trimmed = q.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String trimOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }
}
