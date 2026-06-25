package com.allcenter.modulebiesse.dto;

public record ScanInterpretResponse(
        String action,
        Long orderId,
        String orderName,
        String partCode,
        Long partId,
        Integer pieceNumber,
        Long piezaId,
        Integer suggestedCantidadEscaneada,
        boolean orderSwitchRequired,
        Long suggestedOrderId,
        String message) {}
