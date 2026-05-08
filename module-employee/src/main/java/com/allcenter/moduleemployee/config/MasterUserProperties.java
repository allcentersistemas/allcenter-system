package com.allcenter.moduleemployee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.master-user")
public record MasterUserProperties(boolean bootstrap, String email, String password) {

    public MasterUserProperties {
        if (email == null || email.isBlank()) {
            email = "master@allcenter.local";
        }
        if (password == null || password.isBlank()) {
            password = "changeMeOnFirstDeploy";
        }
    }
}
