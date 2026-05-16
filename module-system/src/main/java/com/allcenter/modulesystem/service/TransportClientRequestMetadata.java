package com.allcenter.modulesystem.service;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Metadatos mínimos de red para auditoría (sin depender de module-system). La IP pública es la mejor
 * aproximación tras proxies habituales.
 */
record TransportClientRequestMetadata(String clientIpPublic, String forwardedForChain, String userAgent) {

    private static final int UA_MAX = 2048;
    private static final int CHAIN_MAX = 2048;

    static TransportClientRequestMetadata from(HttpServletRequest request) {
        String xff = trimToNull(request.getHeader("X-Forwarded-For"));
        if (xff != null && xff.length() > CHAIN_MAX) {
            xff = xff.substring(0, CHAIN_MAX);
        }
        String clientPublic = resolveClientPublicIp(request, trimToNull(request.getRemoteAddr()), xff);
        String ua = trimToNull(request.getHeader("User-Agent"));
        if (ua != null && ua.length() > UA_MAX) {
            ua = ua.substring(0, UA_MAX);
        }
        return new TransportClientRequestMetadata(clientPublic, xff, ua);
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
        return truncate(directRemote, 128);
    }

    private static String firstIp(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String first = headerValue.split(",")[0].trim();
        return first.isEmpty() ? null : truncate(first, 128);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
