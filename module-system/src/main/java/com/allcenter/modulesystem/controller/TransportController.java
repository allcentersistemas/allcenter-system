package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.TransportDtos.AddGuiaPaleRequest;
import com.allcenter.modulesystem.dto.TransportDtos.ApiMessage;
import com.allcenter.modulesystem.dto.TransportDtos.CreateGuiaRequest;
import com.allcenter.modulesystem.dto.TransportDtos.CreateTransporteRequest;
import com.allcenter.modulesystem.dto.TransportDtos.GuiaHeaderDto;
import com.allcenter.modulesystem.dto.TransportDtos.GuiaResponse;
import com.allcenter.modulesystem.dto.TransportDtos.TransporteDto;
import com.allcenter.modulesystem.dto.TransportDtos.UpdateGuiaRequest;
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

    @GetMapping("/guias")
    public ResponseEntity<List<GuiaHeaderDto>> listGuias() {
        return ResponseEntity.ok(transportService.listGuias());
    }

    @GetMapping("/guias/{id}")
    public ResponseEntity<GuiaResponse> getGuia(@PathVariable Long id) {
        return ResponseEntity.ok(transportService.getGuiaById(id));
    }

    @PostMapping("/guias")
    public ResponseEntity<GuiaResponse> createGuia(@Valid @RequestBody CreateGuiaRequest request) {
        return ResponseEntity.ok(transportService.createGuia(request));
    }

    @PutMapping("/guias/{id}")
    public ResponseEntity<GuiaResponse> updateGuia(
            @PathVariable Long id,
            @RequestBody UpdateGuiaRequest request) {
        return ResponseEntity.ok(transportService.updateGuia(id, request));
    }

    @PostMapping("/guias/{id}/pales")
    public ResponseEntity<GuiaResponse> addPale(
            @PathVariable Long id,
            @Valid @RequestBody AddGuiaPaleRequest request) {
        return ResponseEntity.ok(transportService.addPale(id, request));
    }

    @DeleteMapping("/guias/{id}/pales/{guiaPaleId}")
    public ResponseEntity<ApiMessage> removePale(
            @PathVariable Long id,
            @PathVariable Long guiaPaleId) {
        return ResponseEntity.ok(transportService.removePale(id, guiaPaleId));
    }
}
