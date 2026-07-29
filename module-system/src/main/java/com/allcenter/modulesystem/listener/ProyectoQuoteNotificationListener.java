package com.allcenter.modulesystem.listener;

import com.allcenter.modulesystem.event.ProyectoQuoteSubmittedEvent;
import com.allcenter.modulesystem.service.EmployeeNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProyectoQuoteNotificationListener {

    private final EmployeeNotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProyectoQuoteSubmitted(ProyectoQuoteSubmittedEvent event) {
        try {
            notificationService.onProyectoQuoteSubmitted(event);
        } catch (Exception ex) {
            log.error(
                    "Error al notificar cotización del proyecto {}: {}",
                    event != null ? event.proyectoId() : null,
                    ex.getMessage(),
                    ex);
        }
    }
}
