package com.allcenter.modulesystem.dto;

import com.allcenter.modulesystem.model.PlanillaAiUsage;
import java.time.Instant;
import java.util.List;

public final class PlanillaAiUsageDtos {

    private PlanillaAiUsageDtos() {}

    public record Summary(
            long totalUploads,
            long successCount,
            long failCount,
            long inputTokens,
            long outputTokens) {}

    public record Item(
            long id,
            Instant createdAt,
            boolean success,
            int filasCount,
            Integer inputTokens,
            Integer outputTokens,
            String provider,
            String model,
            String rejectReason,
            String originalFilename,
            Long bytes) {

        public static Item from(PlanillaAiUsage u) {
            return new Item(
                    u.getId(),
                    u.getCreatedAt(),
                    u.isSuccess(),
                    u.getFilasCount(),
                    u.getInputTokens(),
                    u.getOutputTokens(),
                    blankToNull(u.getProvider()),
                    blankToNull(u.getModel()),
                    blankToNull(u.getRejectReason()),
                    blankToNull(u.getOriginalFilename()),
                    u.getBytes());
        }

        private static String blankToNull(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return raw.trim();
        }
    }

    public record ClientUsageResponse(
            List<Item> items, int page, int size, long totalElements, Summary summary) {}

    public record GlobalSummary(
            Summary today, Summary last30Days, Summary allTime, int dailyLimitPerClient) {}
}
