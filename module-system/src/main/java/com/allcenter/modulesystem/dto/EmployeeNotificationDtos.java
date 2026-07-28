package com.allcenter.modulesystem.dto;

import com.allcenter.modulesystem.model.EmployeeNotificationType;
import java.time.LocalDateTime;

public final class EmployeeNotificationDtos {

    private EmployeeNotificationDtos() {}

    public record UnreadCountResponse(long unreadCount) {}

    public record NotificationItemResponse(
            Long id,
            EmployeeNotificationType type,
            String title,
            String body,
            ProyectoCotizacionPayload payload,
            boolean read,
            LocalDateTime createdAt) {}

    public record ProyectoCotizacionPayload(Long proyectoId, String proyectoNombre, String cliente) {}

    /** Payload enviado por SSE al conectarse o al recibir un evento en vivo. */
    public record LiveNotificationPayload(
            String event,
            Long notificationId,
            EmployeeNotificationType type,
            String title,
            String body,
            ProyectoCotizacionPayload payload,
            long unreadCount) {}
}
