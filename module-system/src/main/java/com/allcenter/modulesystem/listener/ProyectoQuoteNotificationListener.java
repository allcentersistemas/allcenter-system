package com.allcenter.modulesystem.listener;

import com.allcenter.modulesystem.event.ProyectoQuoteSubmittedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ya no se usa: la notificación se programa con {@code TransactionSynchronization} desde
 * {@link com.allcenter.modulesystem.service.OrderPersistenceService} para no depender del event bus.
 * Se deja el listener vacío por si queda algún publishEvent legado.
 */
@Component
@Slf4j
public class ProyectoQuoteNotificationListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProyectoQuoteSubmitted(ProyectoQuoteSubmittedEvent event) {
        // Intentional no-op: OrderPersistenceService.scheduleProyectoQuoteNotification
        log.debug(
                "Evento cotización proyecto {} ignorado (notificación ya programada en persistencia)",
                event != null ? event.proyectoId() : null);
    }
}
