package com.allcenter.modulesystem.config;

import com.allcenter.modulesystem.model.ClientUser;
import com.allcenter.modulesystem.repository.ClientUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "app.client.demo-user", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ClientDemoUserBootstrap implements ApplicationRunner {

    private final ClientUserRepository clientUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClientDemoUserProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (clientUserRepository.count() > 0) {
            return;
        }
        if (!StringUtils.hasText(properties.email()) || !StringUtils.hasText(properties.password())) {
            return;
        }
        ClientUser demo = new ClientUser();
        demo.setEmail(properties.email().trim().toLowerCase());
        demo.setPassword(passwordEncoder.encode(properties.password()));
        demo.setDisplayName(
                StringUtils.hasText(properties.displayName()) ? properties.displayName().trim() : "Cliente Demo");
        demo.setCompanyName(StringUtils.hasText(properties.companyName()) ? properties.companyName().trim() : null);
        demo.setPhone(StringUtils.hasText(properties.phone()) ? properties.phone().trim() : null);
        demo.setTaxId(StringUtils.hasText(properties.taxId()) ? properties.taxId().trim() : null);
        demo.setActive(true);
        clientUserRepository.save(demo);
    }
}
