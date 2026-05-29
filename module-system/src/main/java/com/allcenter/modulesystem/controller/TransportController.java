package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.TransportDtos.CreateTransporteRequest;
import com.allcenter.modulesystem.dto.TransportDtos.TransporteDto;
import com.allcenter.modulesystem.dto.TransportDtos.UpdateTransporteRequest;
import com.allcenter.modulesystem.service.TransportService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("@portalAuth.canRead()")
    public ResponseEntity<List<TransporteDto>> listVehiculos() {
        return ResponseEntity.ok(transportService.listTransportes());
    }

    @GetMapping("/vehiculos/{id}")
    @PreAuthorize("@portalAuth.canRead()")
    public ResponseEntity<TransporteDto> getVehiculo(@PathVariable Long id) {
        return ResponseEntity.ok(transportService.getTransporte(id));
    }

    @PostMapping("/vehiculos")
    @PreAuthorize("@portalAuth.canCreate()")
    public ResponseEntity<TransporteDto> createVehiculo(@Valid @RequestBody CreateTransporteRequest request) {
        return ResponseEntity.ok(transportService.createTransporte(request));
    }

    @PutMapping("/vehiculos/{id}")
    @PreAuthorize("@portalAuth.canUpdate()")
    public ResponseEntity<TransporteDto> updateVehiculo(
            @PathVariable Long id,
            @RequestBody UpdateTransporteRequest request) {
        return ResponseEntity.ok(transportService.updateTransporte(id, request));
    }
}
