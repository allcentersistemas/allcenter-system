package com.allcenter.modulepale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {"com.allcenter.modulepale.model", "com.allcenter.modulelocation.model"})
@EnableJpaRepositories(basePackages = {"com.allcenter.modulepale.repository", "com.allcenter.modulelocation.repository"})
public class ModulePaleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModulePaleApplication.class, args);
    }

}
