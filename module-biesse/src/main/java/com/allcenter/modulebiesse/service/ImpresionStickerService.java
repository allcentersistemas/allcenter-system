package com.allcenter.modulebiesse.service;

import com.allcenter.modulebiesse.dto.ImpresionStickerRequest;
import com.allcenter.modulebiesse.dto.ImpresionStickerResponse;
import com.allcenter.modulebiesse.model.ImpresionSticker;
import com.allcenter.modulebiesse.model.ImpresionStickerDetalle;
import com.allcenter.modulebiesse.repository.ImpresionStickerRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Lógica para registrar y consultar impresiones de stickers / etiquetas de pieza.
 * Cada llamada {@link #register} crea un evento de auditoría con uno o varios detalles
 * (uno por pieza impresa). Junto con los datos del JWT capturamos IP, equipo y user-agent
 * para construir trazabilidad completa de quién imprimió qué, cuándo y desde dónde.
 */
@Service
@RequiredArgsConstructor
public class ImpresionStickerService {

    private final ImpresionStickerRepository repository;

    @Transactional
    public ImpresionStickerResponse register(
            Long employeeId, String clientIp, ImpresionStickerRequest request) {
        if (request.detalles() == null || request.detalles().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "detalles is required");
        }

        ImpresionSticker entity = new ImpresionSticker();
        entity.setUsuarioId(employeeId);
        entity.setOrderId(request.orderId());
        entity.setMetodo(normalize(request.metodo(), "MANUAL"));
        entity.setEquipo(trunc(request.equipo(), 128));
        entity.setUbicacion(trunc(request.ubicacion(), 128));
        entity.setObservaciones(trunc(request.observaciones(), 512));
        entity.setUserAgent(trunc(request.userAgent(), 512));
        entity.setDireccionIp(trunc(clientIp, 64));
        entity.setFecha(OffsetDateTime.now());
        entity.setCantidadEtiquetas(request.detalles().size());

        for (ImpresionStickerRequest.Detalle d : request.detalles()) {
            ImpresionStickerDetalle line = new ImpresionStickerDetalle();
            line.setPartId(d.partId());
            line.setPiezaId(d.piezaId());
            line.setNumeroPieza(d.numeroPieza());
            line.setCodigoQr(trunc(d.codigoQr(), 256));
            line.setSnapshot(d.snapshot());
            entity.addDetalle(line);
        }

        ImpresionSticker saved = repository.save(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ImpresionStickerResponse> search(
            Long orderId, OffsetDateTime from, OffsetDateTime to, int limit) {
        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(limit, 500)));
        return repository.search(orderId, from, to, pageable).stream()
                .map(ImpresionStickerService::toResponse)
                .toList();
    }

    private static ImpresionStickerResponse toResponse(ImpresionSticker e) {
        List<ImpresionStickerResponse.Detalle> det =
                e.getDetalles() == null
                        ? List.of()
                        : e.getDetalles().stream()
                                .map(
                                        l ->
                                                new ImpresionStickerResponse.Detalle(
                                                        l.getId(),
                                                        l.getPartId(),
                                                        l.getPiezaId(),
                                                        l.getNumeroPieza(),
                                                        l.getCodigoQr(),
                                                        l.getSnapshot(),
                                                        l.getFecha()))
                                .toList();
        return new ImpresionStickerResponse(
                e.getId(),
                e.getUsuarioId(),
                e.getOrderId(),
                e.getMetodo(),
                e.getEquipo(),
                e.getUbicacion(),
                e.getDireccionIp(),
                e.getUserAgent(),
                e.getCantidadEtiquetas(),
                e.getObservaciones(),
                e.getFecha(),
                det);
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase();
    }

    private static String trunc(String value, int max) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > max ? t.substring(0, max) : t;
    }
}
