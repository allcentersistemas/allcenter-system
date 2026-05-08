package com.allcenter.modulelocation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public class UbicacionDtos {

    public record CatalogDto(List<UbicacionDto> locations) {}

    public record CreateUbicacionRequest(
            @NotNull String nombre,
            String direccion,
            String distrito,
            String departamento,
            String ciudad) {}

    public record UbicacionDto(Long id, String nombre, String direccion, String distrito, String departamento, String ciudad) {}

}
