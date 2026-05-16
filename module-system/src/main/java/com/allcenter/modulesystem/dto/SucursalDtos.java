package com.allcenter.modulesystem.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public class SucursalDtos {

    public record CatalogDto(List<SucursalDto> branches) {}

    public record CreateSucursalRequest(
            @NotNull String nombre, String direccion, String ciudad, String departamento) {}

    public record SucursalDto(Long id, String nombre, String direccion, String ciudad, String departamento) {}

}
