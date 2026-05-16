package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.model.Orden;
import com.allcenter.modulesystem.model.OrdenDetalle;
import com.allcenter.modulesystem.model.Proyecto;
import com.allcenter.modulesystem.repository.OrdenDetalleRepository;
import com.allcenter.modulesystem.repository.OrdenRepository;
import com.allcenter.modulesystem.repository.ProyectoRepository;
import com.allcenter.modulesystem.dto.OrderDtos;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderPersistenceService {

    private final ProyectoRepository proyectoRepository;
    private final OrdenRepository ordenRepository;
    private final OrdenDetalleRepository ordenDetalleRepository;
    private final ObjectMapper objectMapper;

    public OrderPersistenceService(
            ProyectoRepository proyectoRepository,
            OrdenRepository ordenRepository,
            OrdenDetalleRepository ordenDetalleRepository,
            ObjectMapper objectMapper
    ) {
        this.proyectoRepository = proyectoRepository;
        this.ordenRepository = ordenRepository;
        this.ordenDetalleRepository = ordenDetalleRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderDtos.ProyectoConOrdenesResponse saveProjectTree(OrderDtos.ProyectoCompuestoPayload payload) {
        if (payload == null || payload.project() == null) {
            throw new IllegalArgumentException("La información del proyecto es obligatoria");
        }
        OrderDtos.ProyectoResponse proyecto = saveProyecto(payload.project());
        if (payload.orders() != null) {
            for (OrderDtos.OrdenCompuestaPayload order : payload.orders()) {
                OrderDtos.OrdenResponse created = saveOrden(
                        proyecto.id(),
                        new OrderDtos.OrdenPayload(order.codigo(), order.descripcion())
                );
                replaceDetalles(created.id(), order.detalles());
            }
        }
        return getProjectTree(proyecto.id());
    }

    @Transactional
    public OrderDtos.ProyectoResponse saveProyecto(OrderDtos.ProyectoPayload payload) {
        if (payload == null || payload.nombre() == null || payload.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del proyecto es obligatorio");
        }
        Proyecto proyecto = new Proyecto();
        proyecto.setNombre(payload.nombre().trim());
        proyecto.setCliente(valueOrNull(payload.cliente()));
        proyecto.setReferencia(valueOrNull(payload.referencia()));
        proyecto.setDescripcion(valueOrNull(payload.descripcion()));
        proyecto.setFechacreacion(LocalDateTime.now());
        proyecto.setCodigoproyecto(System.currentTimeMillis());
        Proyecto saved = proyectoRepository.save(proyecto);
        return toProyectoResponse(saved);
    }

    @Transactional
    public OrderDtos.OrdenResponse saveOrden(Long proyectoId, OrderDtos.OrdenPayload payload) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new EntityNotFoundException("Proyecto no encontrado"));
        if (payload == null || payload.codigo() == null || payload.codigo().isBlank()) {
            throw new IllegalArgumentException("El código de la orden es obligatorio");
        }
        Orden orden = new Orden();
        orden.setProyectoId(proyecto);
        orden.setOrderCode(payload.codigo().trim());
        orden.setDescripcion(valueOrNull(payload.descripcion()));
        orden.setOrderName(payload.codigo().trim());
        Orden saved = ordenRepository.save(orden);
        return toOrdenResponse(saved);
    }

    @Transactional
    public List<OrderDtos.DetalleResponse> replaceDetalles(Long ordenId, List<OrderDtos.DetallePayload> payloads) {
        Orden orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> new EntityNotFoundException("Orden no encontrada"));
        ordenDetalleRepository.deleteByOrdenId_Id(ordenId);
        if (payloads == null || payloads.isEmpty()) {
            return List.of();
        }
        List<OrdenDetalle> created = payloads.stream()
                .map(payload -> fromDetallePayload(orden, payload))
                .map(ordenDetalleRepository::save)
                .toList();
        return created.stream().map(this::toDetalleResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderDtos.ProyectoConOrdenesResponse getProjectTree(Long proyectoId) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new EntityNotFoundException("Proyecto no encontrado"));

        List<OrderDtos.OrdenConDetallesResponse> orders = ordenRepository.findByProyectoId_IdOrderByIdAsc(proyectoId)
                .stream()
                .map(orden -> {
                    List<OrderDtos.DetalleResponse> detalles = ordenDetalleRepository.findByOrdenId_IdOrderByIdAsc(orden.getId())
                            .stream()
                            .map(this::toDetalleResponse)
                            .toList();
                    return new OrderDtos.OrdenConDetallesResponse(
                            orden.getId(),
                            orden.getProyectoId().getId(),
                            orden.getOrderCode(),
                            orden.getDescripcion(),
                            detalles
                    );
                }).toList();

        return new OrderDtos.ProyectoConOrdenesResponse(toProyectoResponse(proyecto), orders);
    }

    private OrderDtos.ProyectoResponse toProyectoResponse(Proyecto proyecto) {
        return new OrderDtos.ProyectoResponse(
                proyecto.getId(),
                proyecto.getCodigoproyecto(),
                proyecto.getNombre(),
                proyecto.getCliente(),
                proyecto.getReferencia(),
                proyecto.getDescripcion(),
                proyecto.getFechacreacion()
        );
    }

    private OrderDtos.OrdenResponse toOrdenResponse(Orden orden) {
        return new OrderDtos.OrdenResponse(
                orden.getId(),
                orden.getProyectoId().getId(),
                orden.getOrderCode(),
                orden.getDescripcion()
        );
    }

    private OrderDtos.DetalleResponse toDetalleResponse(OrdenDetalle detalle) {
        Map<String, Object> extras = parseJsonMap(detalle.getParametros());
        return new OrderDtos.DetalleResponse(
                detalle.getId(),
                detalle.getOrdenId().getId(),
                nullToEmpty(detalle.getMaterial()),
                detalle.getCantidad() == null ? "" : detalle.getCantidad().toString(),
                nullToEmpty(detalle.getVeta()),
                detalle.getAncho() == null ? "" : detalle.getAncho().toString(),
                str(extras.get("l1")),
                str(extras.get("l2")),
                str(extras.get("a1")),
                str(extras.get("a2")),
                str(extras.get("perforacionCantidad")),
                str(extras.get("perforacionLado1")),
                str(extras.get("perforacionLado2")),
                str(extras.get("ranuraDist")),
                str(extras.get("ranuraProf")),
                str(extras.get("ranuraEs")),
                "true".equalsIgnoreCase(str(extras.get("observado"))),
                valueOrNull(detalle.getDescripcion())
        );
    }

    private OrdenDetalle fromDetallePayload(Orden orden, OrderDtos.DetallePayload payload) {
        OrdenDetalle detalle = new OrdenDetalle();
        detalle.setOrdenId(orden);
        detalle.setMaterial(valueOrNull(payload.tablero()));
        detalle.setCantidad(parseInteger(payload.cantidad()));
        detalle.setLargo(parseInteger(payload.largoVeta()));
        detalle.setAncho(parseInteger(payload.ancho()));
        detalle.setVeta(valueOrNull(payload.largoVeta()));
        detalle.setDescripcion(valueOrNull(payload.observacion()));

        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("l1", payload.l1());
        extras.put("l2", payload.l2());
        extras.put("a1", payload.a1());
        extras.put("a2", payload.a2());
        extras.put("perforacionCantidad", payload.perforacionCantidad());
        extras.put("perforacionLado1", payload.perforacionLado1());
        extras.put("perforacionLado2", payload.perforacionLado2());
        extras.put("ranuraDist", payload.ranuraDist());
        extras.put("ranuraProf", payload.ranuraProf());
        extras.put("ranuraEs", payload.ranuraEs());
        extras.put("observado", payload.observado());
        detalle.setParametros(writeJson(extras));
        detalle.setDescripcion1(payload.observado() ? "OK" : null);
        return detalle;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String writeJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> parseJsonMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String valueOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
