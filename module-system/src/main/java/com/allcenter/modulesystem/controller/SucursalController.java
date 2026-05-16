package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.SucursalDtos;
import com.allcenter.modulesystem.service.SucursalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class SucursalController {

    private final SucursalService sucursalService;

    @GetMapping("/branches")
    public ResponseEntity<List<SucursalDtos.SucursalDto>> branches() {
        return ResponseEntity.ok(sucursalService.getBranches());
    }

    @PostMapping("/branch")
    public ResponseEntity<SucursalDtos.SucursalDto> createBranch(@Valid @RequestBody SucursalDtos.CreateSucursalRequest request) {
        return ResponseEntity.ok(sucursalService.createBranch(request));
    }
}
