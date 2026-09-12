package com.flowops.auth.application.shared;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowops.auth")
public record AuthProperties(
        Duration passcodeLifetime,
        int passcodeFailureCeiling,
        Duration reauthenticationWindow,
        Duration rateLimitWindow,
        int failuresPerEmail,
        int failuresPerAddress,
        Duration resetTokenLifetime,
        Duration resetRateLimitWindow,
        int resetsPerEmail,
        int resetsPerAddress,
        String resetLinkBase,
        String notificationSender) {}
