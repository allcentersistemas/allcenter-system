package com.allcenter.modulebiesse.dto;

public record ScanInterpretRequest(String code, Long currentOrderId, Boolean confirmOrderSwitch) {}
