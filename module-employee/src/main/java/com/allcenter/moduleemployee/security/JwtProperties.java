package com.allcenter.moduleemployee.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long accessExpirationMs, long refreshExpirationMs) {}
