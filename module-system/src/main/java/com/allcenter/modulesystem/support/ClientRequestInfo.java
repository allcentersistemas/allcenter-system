package com.allcenter.modulesystem.support;

import jakarta.servlet.http.HttpServletRequest;

/** IP y hostname reportados por el cliente en login/refresh. */
public record ClientRequestInfo(String clientIp, String clientHostname) {

    public static ClientRequestInfo from(HttpServletRequest request) {
        if (request == null) {
            return new ClientRequestInfo(null, null);
        }
        String ip = resolveClientIp(request);
        String host = request.getHeader("X-Client-Hostname");
        if (host != null) {
            host = host.trim();
            if (host.isEmpty()) {
                host = null;
            }
        }
        return new ClientRequestInfo(ip, host);
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remote = request.getRemoteAddr();
        if (remote != null && !remote.isBlank()) {
            return remote.trim();
        }
        return null;
    }
}
