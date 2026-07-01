package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.AppConfigDto;
import com.allcenter.modulesystem.dto.AppConfigUpdateRequest;
import com.allcenter.modulesystem.dto.KardexResetResult;
import com.allcenter.modulesystem.dto.MailTestRequest;
import com.allcenter.modulesystem.service.AppConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
public class AppConfigController {

    private final AppConfigService appConfigService;

    @GetMapping
    @PreAuthorize("@portalAuth.canGestion()")
    public ResponseEntity<AppConfigDto> getConfig() {
        return ResponseEntity.ok(appConfigService.getConfig());
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
}
