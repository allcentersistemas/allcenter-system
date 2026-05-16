package com.allcenter.modulesystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inv_stock_movement")
@Getter
@Setter
@NoArgsConstructor
public class InvStockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private InvItem item;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityChange;

    @Column(nullable = false, length = 256)
    private String reason;

    @Column(length = 128)
    private String externalRef;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(length = 320)
    private String createdByEmail;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
