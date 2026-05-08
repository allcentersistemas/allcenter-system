package com.allcenter.modulepale.controller;

import com.allcenter.modulepale.dto.PaleDtos.ApiMessage;
import com.allcenter.modulepale.dto.PaleDtos.CatalogDto;
import com.allcenter.modulepale.dto.PaleDtos.ClosePaleRequest;
import com.allcenter.modulepale.dto.PaleDtos.CreatePaleRequest;
import com.allcenter.modulepale.dto.PaleDtos.CreateSucursalRequest;
import com.allcenter.modulepale.dto.PaleDtos.CreateUbicacionRequest;
import com.allcenter.modulepale.dto.PaleDtos.PaleDetailResponse;
import com.allcenter.modulepale.dto.PaleDtos.PaleHeaderDto;
import com.allcenter.modulepale.dto.PaleDtos.ScanPieceToPaleRequest;
import com.allcenter.modulepale.dto.PaleDtos.SucursalDto;
import com.allcenter.modulepale.dto.PaleDtos.UbicacionDto;
import com.allcenter.modulepale.service.PaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/pallets")
@RequiredArgsConstructor
public class PaleEnvioController {

    private final PaleService paleService;

    @GetMapping("/catalogs")
    public ResponseEntity<CatalogDto> catalogs() {
        return ResponseEntity.ok(paleService.getCatalogs());
    }

    @GetMapping
    public ResponseEntity<List<PaleHeaderDto>> list() {
        return ResponseEntity.ok(paleService.listPallets());
    }

    @PostMapping
    public ResponseEntity<PaleDetailResponse> create(@Valid @RequestBody CreatePaleRequest request) {
        return ResponseEntity.ok(paleService.createPale(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaleDetailResponse> byId(@PathVariable Long id) {
        return ResponseEntity.ok(paleService.getById(id));
    }

    @GetMapping("/by-code/{code}")
    public ResponseEntity<PaleDetailResponse> byCode(@PathVariable String code) {
        return ResponseEntity.ok(paleService.getByCode(code));
    }

    @PostMapping("/{id}/scan-piece")
    public ResponseEntity<ApiMessage> scanPiece(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long id,
            @Valid @RequestBody ScanPieceToPaleRequest request) {
        return ResponseEntity.ok(paleService.scanPiece(authorization, id, request));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<ApiMessage> close(@PathVariable Long id, @RequestBody(required = false) ClosePaleRequest request) {
        return ResponseEntity.ok(paleService.closePale(id, request == null ? new ClosePaleRequest(null) : request));
    }

}
