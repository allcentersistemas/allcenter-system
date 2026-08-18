package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.AppConfigDto;
import com.allcenter.modulesystem.dto.AppConfigUpdateRequest;
import com.allcenter.modulesystem.dto.KardexResetResult;
import com.allcenter.modulesystem.dto.MailTestRequest;
import com.allcenter.modulesystem.dto.PlanillaAiUsageDtos;
import com.allcenter.modulesystem.service.AppConfigService;
import com.allcenter.modulesystem.service.PlanillaAiUsageService;
import com.allcenter.modulesystem.support.OptimizacionStorageService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
public class AppConfigController {

    private final AppConfigService appConfigService;
    private final OptimizacionStorageService optimizacionStorageService;
    private final PlanillaAiUsageService planillaAiUsageService;

    @GetMapping
    @PreAuthorize("@portalAuth.canGestion()")
    public ResponseEntity<AppConfigDto> getConfig() {
        return ResponseEntity.ok(appConfigService.getConfig());
    }

    @GetMapping("/ai-usage/summary")
    @PreAuthorize("@portalAuth.canGestion()")
    public ResponseEntity<PlanillaAiUsageDtos.GlobalSummary> aiUsageSummary() {
        return ResponseEntity.ok(planillaAiUsageService.getGlobalSummary());
    }

    @GetMapping("/ai-usage/rankings")
    @PreAuthorize("@portalAuth.canRead()")
    public ResponseEntity<PlanillaAiUsageDtos.RankingResponse> aiUsageRankings() {
        return ResponseEntity.ok(planillaAiUsageService.getRankings());
    }

    @PutMapping
    @PreAuthorize("@portalAuth.canGestion()")
    public ResponseEntity<AppConfigDto> updateConfig(@Valid @RequestBody AppConfigUpdateRequest request) {
        return ResponseEntity.ok(appConfigService.updateConfig(request));
    }

    @PostMapping("/mail/test")
    @PreAuthorize("@portalAuth.canGestion()")
    public ResponseEntity<Void> testMail(@Valid @RequestBody MailTestRequest request) {
        appConfigService.sendTestMail(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/kardex/reset")
    @PreAuthorize("@portalAuth.isSystem()")
    public ResponseEntity<KardexResetResult> resetKardex() {
        return ResponseEntity.ok(appConfigService.resetKardex());
    }

    @GetMapping("/plantilla-planilla")
    @PreAuthorize("@portalAuth.canGestion()")
    public ResponseEntity<?> getPlantillaPlanilla(@RequestParam(defaultValue = "false") boolean download) {
        OptimizacionStorageService.PlantillaInfo info = optimizacionStorageService.getPlantillaInfo();
        if (!download) {
            return ResponseEntity.ok(
                    Map.of(
                            "available", info.available(),
                            "filename", info.filename() == null ? "" : info.filename(),
                            "sizeBytes", info.sizeBytes(),
                            "uploadedAt", info.uploadedAt() == null ? "" : info.uploadedAt()));
        }
        Resource resource = optimizacionStorageService.loadPlantilla();
        String name = optimizacionStorageService.plantillaDownloadName().replace("\"", "");
        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .body(resource);
    }

    @PostMapping(value = "/plantilla-planilla", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@portalAuth.canGestion()")
    public ResponseEntity<Map<String, Object>> uploadPlantillaPlanilla(@RequestPart("file") MultipartFile file)
            throws IOException {
        String filename = optimizacionStorageService.savePlantilla(file);
        OptimizacionStorageService.PlantillaInfo info = optimizacionStorageService.getPlantillaInfo();
        return ResponseEntity.ok(
                Map.of(
                        "available", true,
                        "filename", filename,
                        "sizeBytes", info.sizeBytes(),
                        "uploadedAt", info.uploadedAt() == null ? "" : info.uploadedAt()));
    }

    @DeleteMapping("/plantilla-planilla")
    @PreAuthorize("@portalAuth.canGestion()")
    public ResponseEntity<Void> deletePlantillaPlanilla() throws IOException {
        optimizacionStorageService.deletePlantilla();
        return ResponseEntity.noContent().build();
    }
}
