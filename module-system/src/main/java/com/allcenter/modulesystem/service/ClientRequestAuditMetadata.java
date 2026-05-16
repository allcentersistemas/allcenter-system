package com.allcenter.modulesystem.service;

import com.allcenter.modulesystem.audit.ClientAuditHeaders;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Extrae metadatos de red y dispositivo de la petición HTTP. La IP "pública" es la mejor aproximación
 * posible tras proxies habituales; la IP "directa" es siempre el peer TCP visto por el servlet.
 */
record ClientRequestAuditMetadata(
        String directRemoteIp,
        String clientIpPublic,
        String clientIpLocal,
        String clientMacAddress,
        String deviceName,
        String deviceId,
        String forwardedForChain,
        String userAgent) {

    private static final int UA_MAX = 2048;
    private static final int CHAIN_MAX = 2048;
    private static final Pattern MAC_NORMALIZER = Pattern.compile("[^0-9A-Fa-f]");

    static ClientRequestAuditMetadata from(HttpServletRequest request) {
        String direct = trimToNull(request.getRemoteAddr());
        String xff = trimToNull(request.getHeader("X-Forwarded-For"));
        if (xff != null && xff.length() > CHAIN_MAX) {
            xff = xff.substring(0, CHAIN_MAX);
        }
        String clientPublic = resolveClientPublicIp(request, direct, xff);
        String localIp = trimToNull(request.getHeader(ClientAuditHeaders.CLIENT_LOCAL_IP));
        if (localIp != null && localIp.length() > 128) {
            localIp = localIp.substring(0, 128);
        }
        String mac = normalizeMac(request.getHeader(ClientAuditHeaders.CLIENT_MAC_ADDRESS));
        String deviceName = resolveDeviceName(request);
        String deviceId = trimToNull(request.getHeader(ClientAuditHeaders.DEVICE_ID));
        if (deviceId != null && deviceId.length() > 128) {
            deviceId = deviceId.substring(0, 128);
        }
        String ua = trimToNull(request.getHeader("User-Agent"));
        if (ua != null && ua.length() > UA_MAX) {
            ua = ua.substring(0, UA_MAX);
        }
        return new ClientRequestAuditMetadata(
                direct, clientPublic, localIp, mac, deviceName, deviceId, xff, ua);
    }

    private static String resolveClientPublicIp(
            HttpServletRequest request, String directRemote, String xffChain) {
        String trueClient = trimToNull(request.getHeader("True-Client-IP"));
        if (trueClient != null) {
            return firstIp(trueClient);
        }
        String cf = trimToNull(request.getHeader("CF-Connecting-IP"));
        if (cf != null) {
            return firstIp(cf);
        }
        if (xffChain != null) {
            return firstIp(xffChain);
        }
        String realIp = trimToNull(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return firstIp(realIp);
        }
        return directRemote;
    }

    private static String firstIp(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String first = headerValue.split(",")[0].trim();
        return first.isEmpty() ? null : truncate(first, 128);
    }

    private static String resolveDeviceName(HttpServletRequest request) {
        String explicit = trimToNull(request.getHeader(ClientAuditHeaders.DEVICE_NAME));
        if (explicit != null) {
            return truncate(explicit, 256);
        }
        String model = stripQuotes(trimToNull(request.getHeader("Sec-CH-UA-Model")));
        String platform = stripQuotes(trimToNull(request.getHeader("Sec-CH-UA-Platform")));
        String mobile = trimToNull(request.getHeader("Sec-CH-UA-Mobile"));
        if (model == null && platform == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (model != null) {
            sb.append(model);
        }
        if (platform != null) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(platform);
        }
        if (mobile != null && !mobile.isBlank()) {
            sb.append(sb.length() > 0 ? " · " : "").append("mobile=").append(mobile);
        }
        return truncate(sb.toString(), 256);
    }

    private static String stripQuotes(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1).trim();
        }
        if ("?".equals(t)) {
            return null;
        }
        return t;
    }

    private static String normalizeMac(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String hex = MAC_NORMALIZER.matcher(raw).replaceAll("");
        if (hex.length() != 12) {
            return truncate(raw.trim().toUpperCase(Locale.ROOT), 32);
        }
        return String.format(
                Locale.ROOT,
                "%s:%s:%s:%s:%s:%s",
                hex.substring(0, 2),
                hex.substring(2, 4),
                hex.substring(4, 6),
                hex.substring(6, 8),
                hex.substring(8, 10),
                hex.substring(10, 12))
                .toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
