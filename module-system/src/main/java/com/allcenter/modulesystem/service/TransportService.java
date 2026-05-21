package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.TransportDtos.CreateTransporteRequest;
import com.allcenter.modulesystem.dto.TransportDtos.TransporteDto;
import com.allcenter.modulesystem.dto.TransportDtos.UpdateTransporteRequest;
import com.allcenter.modulesystem.model.TransportAuditAction;
import com.allcenter.modulesystem.model.TransportAuditEntityTypes;
import com.allcenter.modulesystem.model.Transporte;
import com.allcenter.modulesystem.repository.TransporteRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class TransportService {

    private final TransporteRepository transporteRepository;
    private final TransportAuditService transportAuditService;

    public List<TransporteDto> listTransportes() {
        return transporteRepository.findAll().stream()
                .map(this::toTransporteDto)
                .toList();
    }

    public TransporteDto getTransporte(Long id) {
        Transporte transporte = transporteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Transporte no encontrado"));
        return toTransporteDto(transporte);
    }

    @Transactional
    public TransporteDto createTransporte(CreateTransporteRequest request) {
        String placa = normalizeRequired(request.placa(), "placa");
        transporteRepository.findByPlacaIgnoreCase(placa)
                .ifPresent(t -> {
                    throw new ResponseStatusException(CONFLICT, "Ya existe un transporte con esa placa");
                });

        Transporte transporte = new Transporte();
        transporte.setPlaca(placa);
        transporte.setNumeroserie(normalizeOptional(request.numeroSerie()));
        transporte.setModelo(normalizeOptional(request.modelo()));
        transporte.setMarca(normalizeOptional(request.marca()));
        transporte.setColor(normalizeOptional(request.color()));
        transporte.setDescripcion(normalizeOptional(request.descripcion()));
        transporte.setTipoVehiculo(normalizeOptional(request.tipoVehiculo()));
        transporte.setCapacidad(request.capacidad());
        transporte.setActivo(request.activo() == null ? Boolean.TRUE : request.activo());
        transporte.setFechaCreacion(LocalDateTime.now());

        transporte = transporteRepository.save(transporte);
        String tid = String.valueOf(transporte.getId());
        transportAuditService.record(
                TransportAuditAction.CREATE,
                TransportAuditEntityTypes.TRANSPORTE,
                tid,
                tid,
                "placa=" + transporte.getPlaca());
        return toTransporteDto(transporte);
    }

    @Transactional
    public TransporteDto updateTransporte(Long id, UpdateTransporteRequest request) {
        Transporte transporte = transporteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Transporte no encontrado"));

        if (request.placa() != null) {
            String placa = normalizeRequired(request.placa(), "placa");
            transporteRepository.findByPlacaIgnoreCase(placa)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(CONFLICT, "La placa ya esta registrada");
                    });
            transporte.setPlaca(placa);
        }
        if (request.numeroSerie() != null) {
            transporte.setNumeroserie(normalizeOptional(request.numeroSerie()));
        }
        if (request.modelo() != null) {
            transporte.setModelo(normalizeOptional(request.modelo()));
        }
        if (request.marca() != null) {
            transporte.setMarca(normalizeOptional(request.marca()));
        }
        if (request.color() != null) {
            transporte.setColor(normalizeOptional(request.color()));
        }
        if (request.descripcion() != null) {
            transporte.setDescripcion(normalizeOptional(request.descripcion()));
        }
        if (request.tipoVehiculo() != null) {
            transporte.setTipoVehiculo(normalizeOptional(request.tipoVehiculo()));
        }
        if (request.capacidad() != null) {
            transporte.setCapacidad(request.capacidad());
        }
        if (request.activo() != null) {
            transporte.setActivo(request.activo());
        }

        transporte = transporteRepository.save(transporte);
        String tid = String.valueOf(transporte.getId());
        transportAuditService.record(
                TransportAuditAction.UPDATE,
                TransportAuditEntityTypes.TRANSPORTE,
                tid,
                tid,
                "placa=" + transporte.getPlaca() + ";activo=" + transporte.getActivo());
        return toTransporteDto(transporte);
    }

    private TransporteDto toTransporteDto(Transporte transporte) {
        return new TransporteDto(
                transporte.getId(),
                transporte.getPlaca(),
                transporte.getNumeroserie(),
                transporte.getModelo(),
                transporte.getMarca(),
                transporte.getColor(),
                transporte.getDescripcion(),
                transporte.getTipoVehiculo(),
                transporte.getCapacidad(),
                transporte.getActivo(),
                transporte.getFechaCreacion());
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new ResponseStatusException(BAD_REQUEST, "El campo " + fieldName + " es obligatorio");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
