package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.OrderDtos;
import com.allcenter.modulesystem.model.Orden;
import com.allcenter.modulesystem.model.ProyectoEstado;
import com.allcenter.modulesystem.model.ProyectoOptimizacion;
import com.allcenter.modulesystem.repository.OrdenRepository;
import com.allcenter.modulesystem.repository.ProyectoRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Avances post-venta desde la app Android (escaneo Biesse). Producción no se automatiza.
 */
@Service
@RequiredArgsConstructor
public class FulfillmentService {

    private final OrdenRepository ordenRepository;
    private final ProyectoRepository proyectoRepository;
    private final OrderPersistenceService orderPersistenceService;

    @Transactional
    public void onAndroidScan(String orderName, String bookingCode, boolean orderComplete) {
        Set<Long> seen = new LinkedHashSet<>();
        for (Orden orden : findOrdenes(orderName, bookingCode)) {
            ProyectoOptimizacion proyecto = orden.getProyectoOptimizacionId();
            if (proyecto == null || proyecto.getId() == null || !seen.add(proyecto.getId())) {
                continue;
            }
            ProyectoOptimizacion current = proyectoRepository.findById(proyecto.getId()).orElse(null);
            if (current == null || current.getEstado() == null || !current.getEstado().isPostVenta()) {
                continue;
            }
            String label = firstNonBlank(orderName, bookingCode);
            if (current.getEstado() == ProyectoEstado.VENDIDO
                    || current.getEstado() == ProyectoEstado.PRODUCCION) {
                orderPersistenceService.advanceFulfillmentInternal(
                        current,
                        ProyectoEstado.DESPACHO,
                        "Primera pieza escaneada en Android (" + label + ")");
                current = proyectoRepository.findById(current.getId()).orElse(current);
            }
            if (orderComplete && current.getEstado() == ProyectoEstado.DESPACHO) {
                orderPersistenceService.advanceFulfillmentInternal(
                        current,
                        ProyectoEstado.LISTO_PARA_ENTREGAR,
                        "Todas las piezas fueron escaneadas en Android (" + label + ")");
            }
        }
    }

    @Transactional
    public OrderDtos.FulfillmentActionResponse markEntregadoByOrder(String orderName, String bookingCode) {
        List<Orden> ordenes = findOrdenes(orderName, bookingCode);
        if (ordenes.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "No hay un proyecto asociado a esta orden");
        }
        Set<Long> seen = new LinkedHashSet<>();
        Long lastId = null;
        boolean advanced = false;
        for (Orden orden : ordenes) {
            ProyectoOptimizacion proyecto = orden.getProyectoOptimizacionId();
            if (proyecto == null || proyecto.getId() == null || !seen.add(proyecto.getId())) {
                continue;
            }
            ProyectoOptimizacion current = proyectoRepository.findById(proyecto.getId()).orElse(null);
            if (current == null || current.getEstado() == null || !current.getEstado().isPostVenta()) {
                continue;
            }
            lastId = current.getId();
            if (current.getEstado() == ProyectoEstado.ENTREGADO) {
                advanced = true;
                continue;
            }
            boolean ok =
                    orderPersistenceService.advanceFulfillmentInternal(
                            current,
                            ProyectoEstado.ENTREGADO,
                            "Marcado entregado desde Android (" + firstNonBlank(orderName, bookingCode) + ")");
            if (ok) {
                advanced = true;
            }
        }
        if (!advanced || lastId == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La orden no está en seguimiento post-venta o ya no se puede marcar entregada");
        }
        return new OrderDtos.FulfillmentActionResponse(true, "Marcado como entregado", lastId);
    }

    private List<Orden> findOrdenes(String orderName, String bookingCode) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        java.util.ArrayList<Orden> out = new java.util.ArrayList<>();
        for (String q : new String[] {orderName, bookingCode}) {
            if (q == null || q.isBlank()) {
                continue;
            }
            for (Orden orden : ordenRepository.findByCodeOrName(q.trim())) {
                if (orden.getId() != null && ids.add(orden.getId())) {
                    out.add(orden);
                }
            }
        }
        return out;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return "orden";
    }
}
