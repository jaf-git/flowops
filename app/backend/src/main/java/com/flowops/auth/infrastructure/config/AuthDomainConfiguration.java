package com.flowops.auth.infrastructure.config;

import com.flowops.auth.domain.service.PasswordPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthDomainConfiguration {
    @Bean
    public PasswordPolicy passwordPolicy() {
        return new PasswordPolicy();
    }
}
