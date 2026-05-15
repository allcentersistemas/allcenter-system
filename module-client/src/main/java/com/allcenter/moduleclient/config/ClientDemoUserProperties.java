package com.allcenter.moduleclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.client.demo-user")
public record ClientDemoUserProperties(
        boolean enabled,
        String email, String password, String displayName, String companyName, String phone, String taxId) {}
