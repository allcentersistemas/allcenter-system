package com.allcenter.moduleemployee.model.dto;

/** {@code true} si aún no hay empleados y se puede llamar a POST /api/auth/first-setup. */
public record FirstSetupStatusResponse(boolean setupRequired) {}
