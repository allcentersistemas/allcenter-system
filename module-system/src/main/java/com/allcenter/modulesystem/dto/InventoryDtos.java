package com.allcenter.modulesystem.dto;

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

    public record CategoriaRow(String codigo, String etiqueta) {}

    public record CreateItemRequest(
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 512) String name,
            @Size(max = 32) String unit) {}

    public record CreateMovementRequest(
            @NotNull BigDecimal quantityChange,
            @NotBlank @Size(max = 256) String reason,
            @Size(max = 128) String externalRef,
            Long sucursalId,
            @Size(max = 32) String categoriaCodigo,
            String observaciones) {

        public CreateMovementRequest(BigDecimal quantityChange, String reason, String externalRef) {
            this(quantityChange, reason, externalRef, null, null, null);
        }
    }

    public record ItemRow(
            long id,
            String sku,
            String name,
            String unit,
            boolean active,
            String familiaCodigo,
            String tipoInventario,
            BigDecimal balanceOnHand,
            Instant createdAt) {

        public ItemRow(
                long id,
                String sku,
                String name,
                String unit,
                boolean active,
                String familiaCodigo,
                Instant createdAt) {
            this(id, sku, name, unit, active, familiaCodigo, null, null, createdAt);
        }

        public ItemRow(
                long id,
                String sku,
                String name,
                String unit,
                boolean active,
                String familiaCodigo,
                String tipoInventario,
                Instant createdAt) {
            this(id, sku, name, unit, active, familiaCodigo, tipoInventario, null, createdAt);
        }
    }

    public record MovementRow(
            long id,
            BigDecimal quantityChange,
            String reason,
            String externalRef,
            Long sucursalId,
            String categoriaCodigo,
            String observaciones,
            Instant createdAt,
            String createdByEmail) {}

    public record BalanceByCategoria(String categoriaCodigo, String categoriaEtiqueta, BigDecimal balance) {}

    public record ItemDetail(
            ItemRow item,
            BigDecimal balanceOnHand,
            Long sucursalId,
            List<BalanceByCategoria> balancesByCategoria,
            List<MovementRow> recentMovements) {}

    /** Artículo del kardex para planilla de corte (portal cliente). */
    public record KardexMaterialOption(
            long id, String sku, String name, String unit, BigDecimal stockOnHand) {}

    public record OptimizacionKardexCatalog(
            List<KardexMaterialOption> tableros, List<KardexMaterialOption> cantos) {}
}
