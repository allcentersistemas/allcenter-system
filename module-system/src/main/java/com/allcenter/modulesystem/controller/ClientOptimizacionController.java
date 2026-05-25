package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.OrderDtos;
import com.allcenter.modulesystem.security.ClientUserDetails;
import com.allcenter.modulesystem.service.OrderPersistenceService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client/optimizacion")
public class ClientOptimizacionController {

    private final OrderPersistenceService service;

    public ClientOptimizacionController(OrderPersistenceService service) {
        this.service = service;
    }

    @GetMapping("/proyectos")
    public List<OrderDtos.ProyectoResumenResponse> listProyectos(
            @AuthenticationPrincipal ClientUserDetails principal) {
        return service.listProjectsForClient(principal.getClientUser().getId());
    }

    @GetMapping("/proyectos/{proyectoId}")
    public OrderDtos.ProyectoConOrdenesResponse getProyecto(
            @AuthenticationPrincipal ClientUserDetails principal, @PathVariable Long proyectoId) {
        return service.getProjectTreeForClient(principal.getClientUser().getId(), proyectoId);
    }

    @PostMapping("/proyectos/guardar-completo")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDtos.ProyectoConOrdenesResponse saveFullProject(
            @AuthenticationPrincipal ClientUserDetails principal,
            @RequestBody OrderDtos.ProyectoCompuestoPayload payload) {
        return service.saveProjectTreeForClient(principal.getClientUser().getId(), payload);
    }

    @ExceptionHandler({IllegalArgumentException.class, EntityNotFoundException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(Exception ex) {
        return Map.of("message", ex.getMessage());
    }
}
