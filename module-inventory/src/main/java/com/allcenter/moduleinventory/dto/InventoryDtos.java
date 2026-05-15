package com.allcenter.moduleinventory.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class InventoryDtos {

    private InventoryDtos() {}

    public record Created(long id) {}

    public record CreateItemRequest(
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 512) String name,
            @Size(max = 32) String unit) {}

    public record CreateMovementRequest(
            @NotNull BigDecimal quantityChange,
            @NotBlank @Size(max = 256) String reason,
            @Size(max = 128) String externalRef) {}

    public record ItemRow(long id, String sku, String name, String unit, boolean active, Instant createdAt) {}

    public record MovementRow(
            long id,
            BigDecimal quantityChange,
            String reason,
            String externalRef,
            Instant createdAt,
            String createdByEmail) {}

    public record ItemDetail(ItemRow item, BigDecimal balanceOnHand, List<MovementRow> recentMovements) {}
}
