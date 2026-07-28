package com.allcenter.modulesystem.listener;

import com.allcenter.modulesystem.event.ProyectoQuoteSubmittedEvent;
import com.allcenter.modulesystem.service.EmployeeNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ProyectoQuoteNotificationListener {

    private final EmployeeNotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProyectoQuoteSubmitted(ProyectoQuoteSubmittedEvent event) {
        notificationService.onProyectoQuoteSubmitted(event);
    }
}
