package com.allcenter.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
public class SharedJwtValidator {

    private final SecretKey signingKey;

    public SharedJwtValidator(@Value("${jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            this.signingKey = null;
            return;
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 32 bytes for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public boolean isConfigured() {
        return signingKey != null;
    }

    public Claims parseClaims(String token) {
        if (signingKey == null) {
            throw new IllegalStateException("jwt.secret is not configured");
        }
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }

    public boolean isValidAccessToken(String token) {
        Claims claims = parseClaims(token);
        Date exp = claims.getExpiration();
        return exp != null && exp.after(new Date());
    }
}
