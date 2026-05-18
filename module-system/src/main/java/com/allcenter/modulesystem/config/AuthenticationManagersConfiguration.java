package com.allcenter.modulesystem.config;

import com.allcenter.modulesystem.security.ClientUserDetailsService;
import com.allcenter.modulesystem.security.EmployeeUserDetailsService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Un AuthenticationManager por dominio (empleados / clientes). Evita que el login de empleados
 * pase por el DaoAuthenticationProvider de clientes (y viceversa).
 */
@Configuration
public class AuthenticationManagersConfiguration {

    @Bean
    @Primary
    @Qualifier("employeeAuthenticationManager")
    AuthenticationManager employeeAuthenticationManager(
            EmployeeUserDetailsService employeeUserDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(employeeUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    @Qualifier("clientAuthenticationManager")
    AuthenticationManager clientAuthenticationManager(
            ClientUserDetailsService clientUserDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(clientUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
