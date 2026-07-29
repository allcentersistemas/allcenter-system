package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.EmployeeNotificationDtos;
import com.allcenter.modulesystem.event.ProyectoQuoteSubmittedEvent;
import com.allcenter.modulesystem.model.Employee;
import com.allcenter.modulesystem.model.EmployeeNotification;
import com.allcenter.modulesystem.model.EmployeeNotificationType;
import com.allcenter.modulesystem.repository.EmployeeNotificationRepository;
import com.allcenter.modulesystem.repository.EmployeeRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeNotificationService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final EmployeeNotificationRepository notificationRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeePermissionService permissionService;
    private final EmployeeNotificationStreamService streamService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public long unreadCount(long employeeId) {
        return notificationRepository.countByEmployeeIdAndReadAtIsNull(employeeId);
    }

    @Transactional(readOnly = true)
    public List<EmployeeNotificationDtos.NotificationItemResponse> listRecent(long employeeId) {
        return notificationRepository.findTop30ByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .map(this::toItem)
                .toList();
    }

    @Transactional
    public void markRead(long employeeId, long notificationId) {
        notificationRepository
                .findByIdAndEmployeeId(notificationId, employeeId)
                .ifPresent(
                        n -> {
                            if (n.getReadAt() == null) {
                                n.setReadAt(LocalDateTime.now(LIMA));
                                notificationRepository.save(n);
                            }
                        });
    }

    @Transactional
    public long markAllRead(long employeeId) {
        List<EmployeeNotification> unread =
                notificationRepository.findByEmployeeIdAndReadAtIsNull(employeeId);
        LocalDateTime now = LocalDateTime.now(LIMA);
        for (EmployeeNotification n : unread) {
            n.setReadAt(now);
        }
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    @Transactional
    public void onProyectoQuoteSubmitted(ProyectoQuoteSubmittedEvent event) {
        if (event == null || event.proyectoId() == null) {
            return;
        }
        String nombre = event.nombre() != null ? event.nombre().trim() : ("Proyecto #" + event.proyectoId());
        String cliente = event.cliente() != null ? event.cliente().trim() : "";
        String title = "Nueva solicitud de cotización";
        String body =
                cliente.isBlank()
                        ? "Proyecto «" + nombre + "»"
                        : "Proyecto «" + nombre + "» · Cliente: " + cliente;
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("proyectoId", event.proyectoId());
        payloadMap.put("proyectoNombre", nombre);
        payloadMap.put("cliente", cliente);
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payloadMap);
        } catch (Exception ex) {
            payloadJson = null;
        }

        List<Employee> recipients =
                employeeRepository.findAllWithRolesActiveOnly(true).stream()
                        .filter(Employee::isActive)
                        .filter(permissionService::canReceiveProyectoOptimizacionNotifications)
                        .toList();

        LocalDateTime now = LocalDateTime.now(LIMA);
        for (Employee employee : recipients) {
            if (employee.getId() == null) {
                continue;
            }
            EmployeeNotification row = new EmployeeNotification();
            row.setEmployeeId(employee.getId());
            row.setNotificationType(EmployeeNotificationType.PROYECTO_COTIZACION);
            row.setTitle(title);
            row.setBody(body);
            row.setPayloadJson(payloadJson);
            row.setCreatedAt(now);
            EmployeeNotification saved = notificationRepository.save(row);
            long unread = notificationRepository.countByEmployeeIdAndReadAtIsNull(employee.getId());
            EmployeeNotificationDtos.LiveNotificationPayload live =
                    new EmployeeNotificationDtos.LiveNotificationPayload(
                            "proyecto-cotizacion",
                            saved.getId(),
                            EmployeeNotificationType.PROYECTO_COTIZACION,
                            title,
                            body,
                            new EmployeeNotificationDtos.ProyectoCotizacionPayload(
                                    event.proyectoId(), nombre, cliente),
                            unread);
            streamService.pushToEmployee(employee.getId(), live);
        }
        log.info(
                "Notificación cotización proyecto {} enviada a {} empleados",
                event.proyectoId(),
                recipients.size());
    }

    private EmployeeNotificationDtos.NotificationItemResponse toItem(EmployeeNotification n) {
        EmployeeNotificationDtos.ProyectoCotizacionPayload payload = parsePayload(n.getPayloadJson());
        return new EmployeeNotificationDtos.NotificationItemResponse(
                n.getId(),
                n.getNotificationType(),
                n.getTitle(),
                n.getBody(),
                payload,
                n.getReadAt() != null,
                n.getCreatedAt());
    }

    private EmployeeNotificationDtos.ProyectoCotizacionPayload parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            Long proyectoId = map.get("proyectoId") instanceof Number num ? num.longValue() : null;
            String nombre = Objects.toString(map.get("proyectoNombre"), null);
            String cliente = Objects.toString(map.get("cliente"), null);
            return new EmployeeNotificationDtos.ProyectoCotizacionPayload(proyectoId, nombre, cliente);
        } catch (Exception ex) {
            return null;
        }
    }
}
