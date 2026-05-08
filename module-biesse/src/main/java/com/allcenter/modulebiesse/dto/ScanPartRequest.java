package com.allcenter.modulebiesse.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ScanPartRequest(
        @NotNull Long partId,
        @NotNull @Min(1) Integer scannedQuantity,
        String observations,
        String equipment,
        String method,
        Integer scanTimeMs,
        String location) {}
