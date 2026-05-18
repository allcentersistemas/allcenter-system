package com.allcenter.modulesystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(boolean enabled, String from, String fromName) {}
