package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.TransportDtos.AddTransporteCargaDetalleRequest;
import com.allcenter.modulesystem.dto.TransportDtos.ApiMessage;
import com.allcenter.modulesystem.dto.TransportDtos.CreateTransporteCargaRequest;
import com.allcenter.modulesystem.dto.TransportDtos.CreateTransporteRequest;
import com.allcenter.modulesystem.dto.TransportDtos.TransporteCargaHeaderDto;
import com.allcenter.modulesystem.dto.TransportDtos.TransporteCargaResponse;
import com.allcenter.modulesystem.dto.TransportDtos.TransporteDto;
import com.allcenter.modulesystem.dto.TransportDtos.UpdateTransporteCargaRequest;
import com.allcenter.modulesystem.dto.TransportDtos.UpdateTransporteRequest;
import com.allcenter.modulesystem.service.TransportService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transport")
@RequiredArgsConstructor
public class TransportController {

    private final TransportService transportService;

    @GetMapping("/vehiculos")
    public ResponseEntity<List<TransporteDto>> listVehiculos() {
        return ResponseEntity.ok(transportService.listTransportes());
    }

    @GetMapping("/vehiculos/{id}")
    public ResponseEntity<TransporteDto> getVehiculo(@PathVariable Long id) {
        return ResponseEntity.ok(transportService.getTransporte(id));
    }

    @PostMapping("/vehiculos")
    public ResponseEntity<TransporteDto> createVehiculo(@Valid @RequestBody CreateTransporteRequest request) {
        return ResponseEntity.ok(transportService.createTransporte(request));
    }

    @PutMapping("/vehiculos/{id}")
    public ResponseEntity<TransporteDto> updateVehiculo(
            @PathVariable Long id,
            @RequestBody UpdateTransporteRequest request) {
        return ResponseEntity.ok(transportService.updateTransporte(id, request));
    }

    @GetMapping("/cargas")
    public ResponseEntity<List<TransporteCargaHeaderDto>> listCargas() {
        return ResponseEntity.ok(transportService.listCargas());
    }

    @GetMapping("/cargas/{id}")
    public ResponseEntity<TransporteCargaResponse> getCarga(@PathVariable Long id) {
        return ResponseEntity.ok(transportService.getCargaById(id));
    }

    @PostMapping("/cargas")
    public ResponseEntity<TransporteCargaResponse> createCarga(@Valid @RequestBody CreateTransporteCargaRequest request) {
        return ResponseEntity.ok(transportService.createCarga(request));
    }

    @PutMapping("/cargas/{id}")
    public ResponseEntity<TransporteCargaResponse> updateCarga(
            @PathVariable Long id,
            @RequestBody UpdateTransporteCargaRequest request) {
        return ResponseEntity.ok(transportService.updateCarga(id, request));
    }

    @PostMapping("/cargas/{id}/detalles")
    public ResponseEntity<TransporteCargaResponse> addDetalle(
            @PathVariable Long id,
            @Valid @RequestBody AddTransporteCargaDetalleRequest request) {
        return ResponseEntity.ok(transportService.addDetalle(id, request));
    }

    @DeleteMapping("/cargas/{id}/detalles/{detalleId}")
    public ResponseEntity<ApiMessage> removeDetalle(
            @PathVariable Long id,
            @PathVariable Long detalleId) {
        return ResponseEntity.ok(transportService.removeDetalle(id, detalleId));
    }
}
