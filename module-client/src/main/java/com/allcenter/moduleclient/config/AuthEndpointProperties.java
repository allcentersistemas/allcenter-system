package com.allcenter.moduleclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthEndpointProperties(boolean registrationEnabled) {}
