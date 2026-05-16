package com.allcenter.modulesystem;

import com.allcenter.modulesystem.config.AuthEndpointProperties;
import com.allcenter.modulesystem.config.ClientDemoUserProperties;
import com.allcenter.modulesystem.config.FirstSetupProperties;
import com.allcenter.modulesystem.config.MasterUserProperties;
import com.allcenter.modulesystem.config.RegistrationProperties;
import com.allcenter.modulesystem.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    JwtProperties.class,
    AuthEndpointProperties.class,
    RegistrationProperties.class,
    FirstSetupProperties.class,
    MasterUserProperties.class,
    ClientDemoUserProperties.class
})
public class ModuleSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModuleSystemApplication.class, args);
    }
}
