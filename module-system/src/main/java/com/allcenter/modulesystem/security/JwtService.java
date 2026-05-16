package com.allcenter.modulesystem.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String ROLES_CLAIM = "roles";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 32 bytes for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UserDetails userDetails) {
        List<String> roles =
                userDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim(ROLES_CLAIM, roles)
                .issuedAt(new Date(now))
                .expiration(new Date(now + properties.accessExpirationMs()))
                .signWith(signingKey)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String subject = parseClaims(token).getSubject();
        Date exp = parseClaims(token).getExpiration();
        return subject.equalsIgnoreCase(userDetails.getUsername()) && exp.after(new Date());
    }

    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }
}
