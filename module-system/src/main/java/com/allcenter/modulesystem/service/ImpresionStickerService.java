package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.ImpresionStickerRequest;
import com.allcenter.modulesystem.dto.ImpresionStickerResponse;
import com.allcenter.modulesystem.exception.BadRequestException;
import com.allcenter.modulesystem.model.ImpresionSticker;
import com.allcenter.modulesystem.model.ImpresionStickerDetalle;
import com.allcenter.modulesystem.repository.ImpresionStickerRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImpresionStickerService {

    private final ImpresionStickerRepository repository;

    @Transactional
    public ImpresionStickerResponse register(
            Long employeeId, String clientIp, ImpresionStickerRequest request) {
        if (request.detalles() == null || request.detalles().isEmpty()) {
            throw new BadRequestException("detalles es obligatorio");
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
        Pageable pageable =
                PageRequest.of(
                        0,
                        Math.max(1, Math.min(limit, 500)),
                        Sort.by(Sort.Direction.DESC, "fecha"));
        Specification<ImpresionSticker> spec =
                Specification.where(orderIdEquals(orderId))
                        .and(fechaFrom(from))
                        .and(fechaTo(to));
        return repository.findAll(spec, pageable).stream()
                .map(ImpresionStickerService::toResponse)
                .toList();
    }

    private static Specification<ImpresionSticker> orderIdEquals(Long orderId) {
        return (root, query, cb) ->
                orderId == null ? cb.conjunction() : cb.equal(root.get("orderId"), orderId);
    }

    private static Specification<ImpresionSticker> fechaFrom(OffsetDateTime from) {
        return (root, query, cb) ->
                from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("fecha"), from);
    }

    private static Specification<ImpresionSticker> fechaTo(OffsetDateTime to) {
        return (root, query, cb) ->
                to == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("fecha"), to);
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
