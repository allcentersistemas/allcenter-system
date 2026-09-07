package com.allcenter.modulesystem.security;

import com.allcenter.modulesystem.exception.TooManyAttemptsException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bloqueo por fuerza bruta en memoria, por clave {@code usuario+ip}. Sin persistencia: se reinicia
 * con la app (aceptable, el objetivo es frenar automatización, no sustituir auditoría).
 */
@Component
public class LoginRateLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final Duration lockDuration;
    private final ConcurrentHashMap<String, Attempts> attemptsByKey = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${app.security.login-rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${app.security.login-rate-limit.window-minutes:15}") long windowMinutes,
            @Value("${app.security.login-rate-limit.lock-minutes:15}") long lockMinutes) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(windowMinutes);
        this.lockDuration = Duration.ofMinutes(lockMinutes);
    }

    public static String key(String prefix, String username, String ip) {
        String u = username == null ? "?" : username.trim().toLowerCase();
        String i = ip == null || ip.isBlank() ? "?" : ip.trim();
        return prefix + ":" + u + ":" + i;
    }

    public void checkAllowed(String key) {
        Attempts attempts = attemptsByKey.get(key);
        if (attempts == null) {
            return;
        }
        Instant now = Instant.now();
        synchronized (attempts) {
            if (attempts.lockedUntil != null && now.isBefore(attempts.lockedUntil)) {
                throw new TooManyAttemptsException(Duration.between(now, attempts.lockedUntil).getSeconds());
            }
        }
    }

    public void recordFailure(String key) {
        Instant now = Instant.now();
        Attempts attempts = attemptsByKey.computeIfAbsent(key, k -> new Attempts());
        synchronized (attempts) {
            if (attempts.windowStart == null || Duration.between(attempts.windowStart, now).compareTo(window) > 0) {
                attempts.windowStart = now;
                attempts.count = 0;
                attempts.lockedUntil = null;
            }
            attempts.count++;
            if (attempts.count >= maxAttempts) {
                attempts.lockedUntil = now.plus(lockDuration);
            }
        }
    }

    public void recordSuccess(String key) {
        attemptsByKey.remove(key);
    }

    private static final class Attempts {
        Instant windowStart;
        int count;
        Instant lockedUntil;
    }
}
