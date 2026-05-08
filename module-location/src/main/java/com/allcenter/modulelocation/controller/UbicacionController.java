package com.allcenter.modulelocation.controller;


import com.allcenter.modulelocation.dto.UbicacionDtos;
import com.allcenter.modulelocation.service.UbicacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class UbicacionController {

    private final UbicacionService ubicacionService;

    @GetMapping("/locations")
    public ResponseEntity<List<UbicacionDtos.UbicacionDto>> locations() {
        return ResponseEntity.ok(ubicacionService.getLocations());
    }

    @PostMapping("/location")
    public ResponseEntity<UbicacionDtos.UbicacionDto> createLocation(@Valid @RequestBody UbicacionDtos.CreateUbicacionRequest request) {
        return ResponseEntity.ok(ubicacionService.createLocation(request));
    }
}
