package com.allcenter.modulesystem.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BackupScheduler {

    private final BackupService backupService;

    @Scheduled(fixedRate = 900_000)
    public void tick() {
        backupService.runScheduledIfDue();
    }
}
