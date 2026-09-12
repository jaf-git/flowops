package com.flowops.shared.config;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

public interface SecurityChainCustomizer {
    void applyTo(HttpSecurity http) throws Exception;
}
