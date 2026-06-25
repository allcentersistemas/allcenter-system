package com.allcenter.modulesystem.config;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppTimezoneConfiguration {

    public static final String APP_ZONE_ID = "America/Lima";

    @PostConstruct
    void configureDefaultTimezone() {
        TimeZone.setDefault(TimeZone.getTimeZone(APP_ZONE_ID));
    }
}
