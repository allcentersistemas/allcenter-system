package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.dto.PlanillaAiUsageDtos;
import com.allcenter.modulesystem.model.PlanillaAiUsage;
import com.allcenter.modulesystem.repository.PlanillaAiUsageRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PlanillaAiUsageService {

    private static final ZoneId APP_ZONE = ZoneId.of("America/Lima");

    private final PlanillaAiUsageRepository repository;
    private final AppConfigService appConfigService;

    @Transactional
    public void logUsage(
            Long clientUserId,
            String provider,
            String model,
            boolean success,
            int filasCount,
            Integer inputTokens,
            Integer outputTokens,
            String rejectReason,
            String originalFilename,
            Long bytes) {
        if (clientUserId == null) {
            return;
        }
        PlanillaAiUsage row = new PlanillaAiUsage();
        row.setClientUserId(clientUserId);
        row.setCreatedAt(Instant.now());
        row.setProvider(trimMax(provider, 32));
        row.setModel(trimMax(model, 80));
        row.setSuccess(success);
        row.setFilasCount(Math.max(0, filasCount));
        row.setInputTokens(inputTokens);
        row.setOutputTokens(outputTokens);
        row.setRejectReason(trimMax(rejectReason, 1000));
        row.setOriginalFilename(trimMax(originalFilename, 260));
        row.setBytes(bytes);
        repository.save(row);
    }

    @Transactional(readOnly = true)
    public long countTodayUsage(long clientUserId) {
        Instant startOfDay = LocalDate.now(APP_ZONE).atStartOfDay(APP_ZONE).toInstant();
        return repository.countByClientUserIdAndCreatedAtGreaterThanEqual(clientUserId, startOfDay);
    }

    @Transactional(readOnly = true)
    public void assertWithinDailyLimit(long clientUserId) {
        int limit = appConfigService.getAiDailyLimitPerClient();
        if (limit <= 0) {
            return;
        }
        long used = countTodayUsage(clientUserId);
        if (used >= limit) {
            throw new com.allcenter.modulesystem.exception.BadRequestException(
                    "Alcanzó el límite diario de importaciones por foto ("
                            + limit
                            + "). Intente mañana o contacte a administración.");
        }
    }

    @Transactional(readOnly = true)
    public PlanillaAiUsageDtos.ClientUsageResponse getClientUsage(long clientUserId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 50);
        Page<PlanillaAiUsage> result =
                repository.findByClientUserIdOrderByCreatedAtDesc(
                        clientUserId, PageRequest.of(safePage, safeSize));
        List<PlanillaAiUsageDtos.Item> items =
                result.getContent().stream().map(PlanillaAiUsageDtos.Item::from).toList();
        return new PlanillaAiUsageDtos.ClientUsageResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                toSummary(firstRow(repository.summarizeByClient(clientUserId))));
    }

    @Transactional(readOnly = true)
    public PlanillaAiUsageDtos.GlobalSummary getGlobalSummary() {
        Instant startOfDay = LocalDate.now(APP_ZONE).atStartOfDay(APP_ZONE).toInstant();
        Instant last30 = Instant.now().minus(30, ChronoUnit.DAYS);
        return new PlanillaAiUsageDtos.GlobalSummary(
                toSummary(firstRow(repository.summarizeSince(startOfDay))),
                toSummary(firstRow(repository.summarizeSince(last30))),
                toSummary(firstRow(repository.summarizeAll())),
                appConfigService.getAiDailyLimitPerClient());
    }

    private static Object[] firstRow(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    private static PlanillaAiUsageDtos.Summary toSummary(Object[] vals) {
        if (vals == null || vals.length < 5) {
            return new PlanillaAiUsageDtos.Summary(0, 0, 0, 0, 0);
        }
        return new PlanillaAiUsageDtos.Summary(
                toLong(vals[0]), toLong(vals[1]), toLong(vals[2]), toLong(vals[3]), toLong(vals[4]));
    }

    private static long toLong(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception ex) {
            return 0L;
        }
    }

    private static String trimMax(String raw, int max) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String t = raw.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
