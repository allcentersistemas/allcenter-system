package com.allcenter.moduleemployee.model.dto;

import com.allcenter.moduleemployee.model.AuditAction;
import com.allcenter.moduleemployee.model.AuditEntry;
import java.time.Instant;

public record AuditEntryResponse(
        Long id,
        Instant occurredAt,
        AuditAction action,
        String entityType,
        String entityId,
        Long actorEmployeeId,
        String actorEmail,
        String directRemoteIp,
        String clientIpPublic,
        String clientIpLocal,
        String clientMacAddress,
        String deviceName,
        String deviceId,
        String forwardedForChain,
        String userAgent,
        String details) {

    public static AuditEntryResponse from(AuditEntry e) {
        return new AuditEntryResponse(
                e.getId(),
                e.getOccurredAt(),
                e.getAction(),
                e.getEntityType(),
                e.getEntityId(),
                e.getActorEmployeeId(),
                e.getActorEmail(),
                e.getDirectRemoteIp(),
                e.getClientIpPublic(),
                e.getClientIpLocal(),
                e.getClientMacAddress(),
                e.getDeviceName(),
                e.getDeviceId(),
                e.getForwardedForChain(),
                e.getUserAgent(),
                e.getDetails());
    }
}
