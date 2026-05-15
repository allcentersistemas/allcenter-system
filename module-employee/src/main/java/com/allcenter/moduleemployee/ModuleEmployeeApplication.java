package com.allcenter.moduleemployee;

import com.allcenter.moduleemployee.config.AuthEndpointProperties;
import com.allcenter.moduleemployee.config.FirstSetupProperties;
import com.allcenter.moduleemployee.config.MasterUserProperties;
import com.allcenter.moduleemployee.config.RegistrationProperties;
import com.allcenter.moduleemployee.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    JwtProperties.class,
    RegistrationProperties.class,
    MasterUserProperties.class,
    FirstSetupProperties.class,
    AuthEndpointProperties.class
})
public class ModuleEmployeeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModuleEmployeeApplication.class, args);
    }

}
