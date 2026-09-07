package com.allcenter.modulesystem.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Login bloqueado temporalmente por demasiados intentos fallidos (ver {@code LoginRateLimiter}). */
@Getter
public class TooManyAttemptsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        super(
                HttpStatus.TOO_MANY_REQUESTS,
                "TOO_MANY_ATTEMPTS",
                "Demasiados intentos fallidos. Intente nuevamente en "
                        + Math.max(1, Math.ceilDiv(retryAfterSeconds, 60))
                        + " min.");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }
}
