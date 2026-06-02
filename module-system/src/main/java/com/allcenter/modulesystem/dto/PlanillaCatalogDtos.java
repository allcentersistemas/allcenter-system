package com.allcenter.modulesystem.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PlanillaCatalogDtos {

    private PlanillaCatalogDtos() {}

    public record TableroRow(
            long id,
            String codigo,
            String nombre,
            Integer espesorMm,
            String unidad,
            boolean active,
            Instant createdAt) {}

    public record CantoRow(long id, String codigo, String nombre, boolean active, Instant createdAt) {}

    public record CreateTableroRequest(
            @NotBlank @Size(max = 64) String codigo,
            @NotBlank @Size(max = 512) String nombre,
            Integer espesorMm,
            @Size(max = 32) String unidad) {}

    public record UpdateTableroRequest(
            @Size(max = 64) String codigo,
            @Size(max = 512) String nombre,
            Integer espesorMm,
            @Size(max = 32) String unidad,
            Boolean active) {}

    public record CreateCantoRequest(
            @NotBlank @Size(max = 64) String codigo, @NotBlank @Size(max = 512) String nombre) {}

    public record UpdateCantoRequest(
            @Size(max = 64) String codigo,
            @Size(max = 512) String nombre,
            Boolean active) {}
}
