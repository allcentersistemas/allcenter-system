package com.allcenter.moduleclient;

import com.allcenter.moduleclient.config.ClientDemoUserProperties;
import com.allcenter.moduleclient.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, ClientDemoUserProperties.class})
public class ModuleClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModuleClientApplication.class, args);
    }

}
