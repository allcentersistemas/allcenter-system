package com.allcenter.modulebiesse.dto;

public record UserScanStatsResponse(
        long totalScanned,
        long scannedToday,
        long scannedThisWeek,
        long scannedThisMonth,
        long totalDifference,
        long contributedOrders) {}
