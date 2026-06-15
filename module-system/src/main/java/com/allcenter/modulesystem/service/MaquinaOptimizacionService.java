package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.OrderDtos;
import com.allcenter.modulesystem.model.MaquinaOptimizacion;
import com.allcenter.modulesystem.repository.MaquinaOptimizacionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaquinaOptimizacionService {

    private final MaquinaOptimizacionRepository repository;

    public MaquinaOptimizacionService(MaquinaOptimizacionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.MaquinaResponse> listActive() {
        return repository.findByActivoTrueOrderByNombreAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.MaquinaResponse> listAll() {
        return repository.findAllByOrderByNombreAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public OrderDtos.MaquinaResponse create(OrderDtos.MaquinaPayload payload) {
        if (payload == null || payload.codigo() == null || payload.codigo().isBlank()) {
            throw new IllegalArgumentException("El código de máquina es obligatorio.");
        }
        if (payload.nombre() == null || payload.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre de máquina es obligatorio.");
        }
        MaquinaOptimizacion entity = new MaquinaOptimizacion();
        entity.setCodigo(payload.codigo().trim());
        entity.setNombre(payload.nombre().trim());
        entity.setActivo(payload.activo() == null || payload.activo());
        entity.setCreatedAt(LocalDateTime.now());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public OrderDtos.MaquinaResponse update(Long id, OrderDtos.MaquinaPayload payload) {
        MaquinaOptimizacion entity = require(id);
        if (payload.codigo() != null && !payload.codigo().isBlank()) {
            entity.setCodigo(payload.codigo().trim());
        }
        if (payload.nombre() != null && !payload.nombre().isBlank()) {
            entity.setNombre(payload.nombre().trim());
        }
        if (payload.activo() != null) {
            entity.setActivo(payload.activo());
        }
        return toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public String resolveParametros(Long maquinaId) {
        if (maquinaId == null) {
            return "";
        }
        return repository.findById(maquinaId).map(MaquinaOptimizacion::getCodigo).orElse("");
    }

    @Transactional(readOnly = true)
    public void requireActiveMaquina(Long maquinaId) {
        if (maquinaId == null) {
            return;
        }
        MaquinaOptimizacion entity = require(maquinaId);
        if (!entity.isActivo()) {
            throw new IllegalArgumentException("La máquina seleccionada no está activa.");
        }
    }

    private MaquinaOptimizacion require(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Máquina no encontrada"));
    }

    private OrderDtos.MaquinaResponse toResponse(MaquinaOptimizacion entity) {
        return new OrderDtos.MaquinaResponse(
                entity.getId(), entity.getCodigo(), entity.getNombre(), entity.isActivo());
    }
}
