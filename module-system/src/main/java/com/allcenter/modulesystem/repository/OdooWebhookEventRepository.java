package com.allcenter.modulesystem.repository;

import com.allcenter.modulesystem.model.OdooWebhookEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OdooWebhookEventRepository extends JpaRepository<OdooWebhookEvent, Long> {

    Page<OdooWebhookEvent> findAllByOrderByReceivedAtDesc(Pageable pageable);

    Page<OdooWebhookEvent> findByTipoOrderByReceivedAtDesc(String tipo, Pageable pageable);
}
