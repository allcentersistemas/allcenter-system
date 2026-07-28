package com.allcenter.modulesystem.controller;

import com.allcenter.modulesystem.dto.EmployeeNotificationDtos;
import com.allcenter.modulesystem.security.EmployeeUserDetails;
import com.allcenter.modulesystem.service.EmployeeNotificationService;
import com.allcenter.modulesystem.service.EmployeeNotificationStreamService;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
public class EmployeeNotificationController {

    private final EmployeeNotificationService notificationService;
    private final EmployeeNotificationStreamService streamService;

    public EmployeeNotificationController(
            EmployeeNotificationService notificationService,
            EmployeeNotificationStreamService streamService) {
        this.notificationService = notificationService;
        this.streamService = streamService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@portalAuth.canViewProyectoOptimizacionNotifications()")
    public SseEmitter stream(@AuthenticationPrincipal EmployeeUserDetails principal) {
        return streamService.connect(principal.getEmployee().getId());
    }

    @GetMapping("/unread-count")
    @PreAuthorize("@portalAuth.canViewProyectoOptimizacionNotifications()")
    public EmployeeNotificationDtos.UnreadCountResponse unreadCount(
            @AuthenticationPrincipal EmployeeUserDetails principal) {
        long count = notificationService.unreadCount(principal.getEmployee().getId());
        return new EmployeeNotificationDtos.UnreadCountResponse(count);
    }

    @GetMapping
    @PreAuthorize("@portalAuth.canViewProyectoOptimizacionNotifications()")
    public List<EmployeeNotificationDtos.NotificationItemResponse> list(
            @AuthenticationPrincipal EmployeeUserDetails principal) {
        return notificationService.listRecent(principal.getEmployee().getId());
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("@portalAuth.canViewProyectoOptimizacionNotifications()")
    public Map<String, Object> markRead(
            @AuthenticationPrincipal EmployeeUserDetails principal, @PathVariable long id) {
        notificationService.markRead(principal.getEmployee().getId(), id);
        long unread = notificationService.unreadCount(principal.getEmployee().getId());
        return Map.of("ok", true, "unreadCount", unread);
    }

    @PostMapping("/read-all")
    @PreAuthorize("@portalAuth.canViewProyectoOptimizacionNotifications()")
    public Map<String, Object> markAllRead(@AuthenticationPrincipal EmployeeUserDetails principal) {
        long marked = notificationService.markAllRead(principal.getEmployee().getId());
        return Map.of("ok", true, "marked", marked, "unreadCount", 0L);
    }
}
