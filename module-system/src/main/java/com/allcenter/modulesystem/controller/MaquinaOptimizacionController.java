package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.OrderDtos;
import com.allcenter.modulesystem.service.MaquinaOptimizacionService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order/maquinas")
public class MaquinaOptimizacionController {

    private final MaquinaOptimizacionService service;

    public MaquinaOptimizacionController(MaquinaOptimizacionService service) {
        this.service = service;
    }

    @GetMapping
    public List<OrderDtos.MaquinaResponse> listAll() {
        return service.listAll();
    }

    @GetMapping("/activas")
    public List<OrderDtos.MaquinaResponse> listActive() {
        return service.listActive();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDtos.MaquinaResponse create(@RequestBody OrderDtos.MaquinaPayload payload) {
        return service.create(payload);
    }

    @PatchMapping("/{id}")
    public OrderDtos.MaquinaResponse update(
            @PathVariable Long id, @RequestBody OrderDtos.MaquinaPayload payload) {
        return service.update(id, payload);
    }

    @ExceptionHandler({IllegalArgumentException.class, EntityNotFoundException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(Exception ex) {
        return Map.of("message", ex.getMessage());
    }
}
