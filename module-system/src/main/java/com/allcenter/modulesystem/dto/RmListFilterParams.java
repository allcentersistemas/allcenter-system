package com.allcenter.modulesystem.dto;

import java.time.LocalDate;

/** Filtros comunes de listados RM (paginados en servidor). */
public record RmListFilterParams(
        String q, LocalDate fechaDesde, LocalDate fechaHasta, String tipoRegistro) {}
