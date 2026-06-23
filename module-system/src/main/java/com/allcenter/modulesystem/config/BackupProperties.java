package com.allcenter.modulesystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.backup")
public record BackupProperties(
        String storageRoot,
        String pgDumpPath,
        String biesseUrl,
        String biesseUsername,
        String biessePassword,
        int maxAttachmentMb) {}
