package com.flowops.shared.config;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    private static final String[] PUBLIC_PATHS = {
        "/api/auth/signup/passcode",
        "/api/auth/signup",
        "/api/auth/login",
        "/api/auth/password-reset/request",
        "/api/auth/password-reset/complete",
        "/api/auth/password-reset/*",
        "/api/auth/logout",
        "/api/workspace/invitations/*",
        "/api/workspace/invitations/*/accept",
        "/api/workspace/invitations/*/decline"
    };

    private static final String[] DOCUMENTATION_PATHS = {
        "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, List<SecurityChainCustomizer> customizers)
            throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);

        for (SecurityChainCustomizer customizer : customizers) {
            customizer.applyTo(http);
        }

        return http.authorizeHttpRequests(requests -> requests.requestMatchers(PUBLIC_PATHS)
                        .permitAll()
                        .requestMatchers(DOCUMENTATION_PATHS)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        .ignoringRequestMatchers(DOCUMENTATION_PATHS))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.NEVER))
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .httpBasic(basic -> basic.disable())
                .formLogin(login -> login.disable())
                .logout(logout -> logout.disable())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityContextRepository.class)
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public UserDetailsService noFrameworkUserStore() {
        return username -> {
            throw new UsernameNotFoundException("Authentication is performed by the login use case.");
        };
    }
}
