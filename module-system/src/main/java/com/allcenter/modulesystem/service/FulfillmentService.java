package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.agent.BiesseObrasClient;
import com.allcenter.modulesystem.dto.OrderDtos;
import com.allcenter.modulesystem.model.Orden;
import com.allcenter.modulesystem.model.ProyectoEstado;
import com.allcenter.modulesystem.model.ProyectoOptimizacion;
import com.allcenter.modulesystem.repository.OrdenRepository;
import com.allcenter.modulesystem.repository.ProyectoRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Avances post-venta desde escaneo Android y agente Biesse (optimizado / producción / despacho).
 * La fuente de verdad del tablero Seguimiento es la obra/XML ({@code estado_escaneo});
 * el proyecto CRM se sincroniza en paralelo cuando hay vínculo.
 */
@Service
@RequiredArgsConstructor
public class FulfillmentService {

    private final OrdenRepository ordenRepository;
    private final ProyectoRepository proyectoRepository;
    private final OrderPersistenceService orderPersistenceService;
    private final BiesseObrasClient biesseObrasClient;

    @Transactional
    public void onAndroidScan(String orderName, String bookingCode, boolean orderComplete) {
        Set<Long> seen = new LinkedHashSet<>();
        String label = firstNonBlank(orderName, bookingCode);
        for (Orden orden : findOrdenes(orderName, bookingCode)) {
            ProyectoOptimizacion proyecto = orden.getProyectoOptimizacionId();
            if (proyecto == null || proyecto.getId() == null || !seen.add(proyecto.getId())) {
                continue;
            }
            ProyectoOptimizacion current = proyectoRepository.findById(proyecto.getId()).orElse(null);
            if (current == null || current.getEstado() == null) {
                continue;
            }
            // El estado del proyecto se recalcula como mínimo de todas las órdenes/XML.
            boolean changed =
                    orderPersistenceService.reconcileProyectoEstadoFromOrdenes(
                            current,
                            "Escaneo Android ("
                                    + label
                                    + (orderComplete ? ", orden completa" : "")
                                    + ")");
            if (!orderComplete) {
                continue;
            }
            current = proyectoRepository.findById(current.getId()).orElse(current);
            // Si el proyecto acabó de pasar a LISTO, reconcile ya envió Telegram.
            // Si aún no (quedan XML pendientes), avisar este pedido/XML.
            if (changed && current.getEstado() == ProyectoEstado.LISTO_PARA_ENTREGAR) {
                continue;
            }
            orderPersistenceService.notifyClientPedidoListoTelegram(current, label);
        }
        orderPersistenceService.notifySeguimientoAfterCommit();
    }

    /**
     * Agente seccionadora marca obra en PRODUCCION → proyecto a PRODUCCION
     * (desde VENDIDO u OPTIMIZADO).
     */
    @Transactional
    public void onObraProduccion(String orderName, String bookingCode) {
        Set<Long> seen = new LinkedHashSet<>();
        String label = firstNonBlank(orderName, bookingCode);
        for (Orden orden : findOrdenes(orderName, bookingCode)) {
            ProyectoOptimizacion proyecto = orden.getProyectoOptimizacionId();
            if (proyecto == null || proyecto.getId() == null || !seen.add(proyecto.getId())) {
                continue;
            }
            ProyectoOptimizacion current = proyectoRepository.findById(proyecto.getId()).orElse(null);
            if (current == null || current.getEstado() == null) {
                continue;
            }
            orderPersistenceService.reconcileProyectoEstadoFromOrdenes(
                    current, "Obra en producción (agente seccionadora: " + label + ")");
        }
        orderPersistenceService.notifySeguimientoAfterCommit();
    }

    /**
     * Seguimiento: Optimizado → Transmitido (PRODUCCION). Manual desde el tablero.
     */
    @Transactional
    public OrderDtos.FulfillmentActionResponse markTransmitidoByBiesseOrderId(long biesseOrderId) {
        boolean changed = biesseObrasClient.markOrderProduccion(biesseOrderId);
        Map<String, Object> after = biesseObrasClient.findOrderById(biesseOrderId);
        if (after == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra Biesse no encontrada");
        }
        String estado =
                firstNonBlank(str(after.get("estado_escaneo")), str(after.get("estadoEscaneo")));
        boolean alreadyProduccionOrBeyond =
                "PRODUCCION".equalsIgnoreCase(estado)
                        || "DESPACHO".equalsIgnoreCase(estado)
                        || "LISTO_PARA_ENTREGAR".equalsIgnoreCase(estado)
                        || "ENTREGADO".equalsIgnoreCase(estado);
        if (!changed && !alreadyProduccionOrBeyond) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Solo se puede transmitir un XML en Optimizado (o pendiente).");
        }
        String orderName = firstNonBlank(str(after.get("orderName")), str(after.get("ordername")));
        String bookingCode =
                firstNonBlank(str(after.get("bookingCode")), str(after.get("bookingcode")));
        onObraProduccion(orderName, bookingCode);
        orderPersistenceService.notifySeguimientoAfterCommit();
        return new OrderDtos.FulfillmentActionResponse(
                true,
                changed ? "XML transmitido a producción" : "XML ya estaba transmitido",
                null,
                biesseOrderId);
    }

    @Transactional
    public OrderDtos.FulfillmentActionResponse markEntregadoByOrder(String orderName, String bookingCode) {
        Map<String, Object> obra =
                biesseObrasClient.markOrderEntregadoByRef(orderName, bookingCode, "android");
        boolean obraOk = obra != null && (Boolean.TRUE.equals(obra.get("changed"))
                || "ENTREGADO".equalsIgnoreCase(String.valueOf(obra.get("estado"))));

        List<Orden> ordenes = findOrdenes(orderName, bookingCode);
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
            orderPersistenceService.reconcileProyectoEstadoFromOrdenes(
                    current,
                    "Marcado entregado desde Android (" + firstNonBlank(orderName, bookingCode) + ")");
            current = proyectoRepository.findById(current.getId()).orElse(current);
            if (current.getEstado() == ProyectoEstado.ENTREGADO) {
                advanced = true;
            }
        }
        if (!advanced && !obraOk) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se pudo marcar entregada la obra/XML ni un proyecto vinculado");
        }
        if (!advanced && obraOk && lastId == null) {
            Object oid = obra.get("orderId");
            Long biesseOrderId = oid instanceof Number n ? n.longValue() : null;
            return new OrderDtos.FulfillmentActionResponse(
                    true, "Obra marcada como entregada", null, biesseOrderId);
        }
        Object oid = obra != null ? obra.get("orderId") : null;
        Long biesseOrderId = oid instanceof Number n ? n.longValue() : null;
        return new OrderDtos.FulfillmentActionResponse(
                true, "Marcado como entregado", lastId, biesseOrderId);
    }

    @Transactional
    public OrderDtos.FulfillmentActionResponse markEntregadoByBiesseOrderId(long biesseOrderId) {
        Map<String, Object> obra = biesseObrasClient.markOrderEntregado(biesseOrderId, "portal");
        if (obra == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra Biesse no encontrada");
        }
        boolean obraOk = Boolean.TRUE.equals(obra.get("changed"))
                || "ENTREGADO".equalsIgnoreCase(String.valueOf(obra.get("estado")));
        String orderName = firstNonBlank(str(obra.get("orderName")), str(obra.get("ordername")));
        String bookingCode = firstNonBlank(str(obra.get("bookingCode")), str(obra.get("bookingcode")));

        List<Orden> ordenes = findOrdenes(orderName, bookingCode);
        if (ordenes.isEmpty()) {
            List<Orden> byId = ordenRepository.findByBiesseOrderId(biesseOrderId);
            ordenes = byId;
        }
        Set<Long> seen = new LinkedHashSet<>();
        Long lastProyectoId = null;
        for (Orden orden : ordenes) {
            ProyectoOptimizacion proyecto = orden.getProyectoOptimizacionId();
            if (proyecto == null || proyecto.getId() == null || !seen.add(proyecto.getId())) {
                continue;
            }
            ProyectoOptimizacion current = proyectoRepository.findById(proyecto.getId()).orElse(null);
            if (current == null || current.getEstado() == null || !current.getEstado().isPostVenta()) {
                continue;
            }
            lastProyectoId = current.getId();
            if (current.getEstado() != ProyectoEstado.ENTREGADO) {
                orderPersistenceService.reconcileProyectoEstadoFromOrdenes(
                        current,
                        "Marcado entregado desde Seguimiento (obra #" + biesseOrderId + ")");
            }
        }
        if (!obraOk && lastProyectoId == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "La obra no está en estado entregable");
        }
        orderPersistenceService.notifySeguimientoAfterCommit();
        return new OrderDtos.FulfillmentActionResponse(
                true, "Obra marcada como entregada", lastProyectoId, biesseOrderId);
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

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
