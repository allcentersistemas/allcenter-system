package com.allcenter.modulebiesse.agent;

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
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        reg.addUrlPatterns("/api/biesse/agent/*");
        return reg;
    }
}
