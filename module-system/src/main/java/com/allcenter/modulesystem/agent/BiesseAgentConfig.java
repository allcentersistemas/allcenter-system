package com.allcenter.modulesystem.agent;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class BiesseAgentConfig {

    @Bean
    FilterRegistrationBean<BiesseAgentAuthFilter> biesseAgentAuthFilter(
            BiesseAgentSchemaAligner schemaAligner) {
        FilterRegistrationBean<BiesseAgentAuthFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new BiesseAgentAuthFilter(schemaAligner));
        reg.setName("biesseAgentAuthFilter");
        reg.addUrlPatterns("/api/biesse/agent/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return reg;
    }
}
