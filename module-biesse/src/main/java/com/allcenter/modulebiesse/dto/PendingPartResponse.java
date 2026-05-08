package com.allcenter.modulebiesse.dto;

public record PendingPartResponse(
        Long partId,
        String partCode,
        String description,
        Integer quantity,
        Double length,
        Double width,
        String material,
        String orderName,
        String bookingCode,
        Long orderId) {}
