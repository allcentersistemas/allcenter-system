package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.OrderDtos;
import com.allcenter.modulesystem.service.OrderPersistenceService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderPersistenceService service;

    public OrderController(OrderPersistenceService service) {
        this.service = service;
    }

    @PostMapping("/proyectos")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDtos.ProyectoResponse createProyecto(@RequestBody OrderDtos.ProyectoPayload payload) {
        return service.saveProyecto(payload);
    }

    @PostMapping("/proyectos/{proyectoId}/ordenes")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDtos.OrdenResponse createOrden(
            @PathVariable Long proyectoId,
            @RequestBody OrderDtos.OrdenPayload payload
    ) {
        return service.saveOrden(proyectoId, payload);
    }

    @PutMapping("/ordenes/{ordenId}/detalles")
    public List<OrderDtos.DetalleResponse> replaceDetalles(
            @PathVariable Long ordenId,
            @RequestBody List<OrderDtos.DetallePayload> payload
    ) {
        return service.replaceDetalles(ordenId, payload);
    }

    @GetMapping("/proyectos/{proyectoId}")
    public OrderDtos.ProyectoConOrdenesResponse getProyecto(@PathVariable Long proyectoId) {
        return service.getProjectTree(proyectoId);
    }

    @PostMapping("/proyectos/guardar-completo")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDtos.ProyectoConOrdenesResponse saveFullProject(
            @RequestBody OrderDtos.ProyectoCompuestoPayload payload
    ) {
        return service.saveProjectTree(payload);
    }

    @ExceptionHandler({IllegalArgumentException.class, EntityNotFoundException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(Exception ex) {
        return Map.of("message", ex.getMessage());
    }
}
