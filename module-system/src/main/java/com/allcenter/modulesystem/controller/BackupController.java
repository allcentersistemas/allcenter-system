package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.BackupConfigDto;
import com.allcenter.modulesystem.dto.BackupConfigUpdateRequest;
import com.allcenter.modulesystem.dto.BackupRestoreFromHistoryRequest;
import com.allcenter.modulesystem.dto.BackupRunDto;
import com.allcenter.modulesystem.service.BackupRestoreService;
import com.allcenter.modulesystem.service.BackupService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/backup")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;
    private final BackupRestoreService backupRestoreService;

    @GetMapping("/config")
    @PreAuthorize("@portalAuth.isMaster()")
    public ResponseEntity<BackupConfigDto> getConfig() {
        return ResponseEntity.ok(backupService.getConfig());
    }

    @PutMapping("/config")
    @PreAuthorize("@portalAuth.isMaster()")
    public ResponseEntity<BackupConfigDto> updateConfig(@Valid @RequestBody BackupConfigUpdateRequest request) {
        return ResponseEntity.ok(backupService.updateConfig(request));
    }

    @PostMapping("/run")
    @PreAuthorize("@portalAuth.isMaster()")
    public ResponseEntity<BackupRunDto> runNow() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(backupService.startManualBackup());
    }

    @GetMapping("/history/{runId}")
    @PreAuthorize("@portalAuth.isMaster()")
    public ResponseEntity<BackupRunDto> getRun(@PathVariable Long runId) {
        return ResponseEntity.ok(backupService.getRun(runId));
    }

    @GetMapping("/history")
    @PreAuthorize("@portalAuth.isMaster()")
    public ResponseEntity<List<BackupRunDto>> history() {
        return ResponseEntity.ok(backupService.listHistory());
    }

    @GetMapping("/history/{runId}/files/{filename}")
    @PreAuthorize("@portalAuth.isMaster()")
    public ResponseEntity<Resource> download(
            @PathVariable Long runId, @PathVariable String filename) {
        Resource resource = backupService.resolveDownloadFile(runId, filename);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    @PostMapping("/restore")
    @PreAuthorize("@portalAuth.isMaster()")
    public ResponseEntity<BackupRunDto> restoreFromHistory(
            @Valid @RequestBody BackupRestoreFromHistoryRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(backupRestoreService.startRestoreFromHistory(
                        request.runId(), request.filename(), request.confirmText()));
    }

    @PostMapping(value = "/restore/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@portalAuth.isMaster()")
    public ResponseEntity<BackupRunDto> restoreUpload(
            @RequestParam String confirmText, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(backupRestoreService.startRestoreUpload(file, confirmText));
    }

    @GetMapping("/restore/history")
    @PreAuthorize("@portalAuth.isMaster()")
    public ResponseEntity<List<BackupRunDto>> restoreHistory() {
        return ResponseEntity.ok(backupRestoreService.listRestoreHistory());
    }
}
