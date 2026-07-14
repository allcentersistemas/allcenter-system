package com.allcenter.modulesystem.dto;

import com.allcenter.modulesystem.model.AuditAction;
import com.allcenter.modulesystem.model.AuditEntry;
import java.time.Instant;

public record ClientLoginEventResponse(
        Long id,
        AuditAction action,
        Instant occurredAt,
        String clientIp,
        String deviceName,
        String userAgent,
        String details) {

    public static ClientLoginEventResponse from(AuditEntry entry) {
        String ip = entry.getClientIpPublic();
        if (ip == null || ip.isBlank()) {
            ip = entry.getDirectRemoteIp();
        }
        return new ClientLoginEventResponse(
                entry.getId(),
                entry.getAction(),
                entry.getOccurredAt(),
                ip,
                entry.getDeviceName(),
                entry.getUserAgent(),
                entry.getDetails());
    }
}
