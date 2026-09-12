package com.flowops.auth.infrastructure.persistence;

import com.flowops.auth.application.shared.AttemptPurpose;
import com.flowops.auth.application.shared.AuthProperties;
import com.flowops.auth.application.shared.port.RateLimitPort;
import com.flowops.auth.infrastructure.persistence.entity.AuthLoginAttemptJpaEntity;
import com.flowops.auth.infrastructure.persistence.repository.AuthLoginAttemptJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RateLimitJdbcAdapter implements RateLimitPort {
    private static final String BY_EMAIL = "EMAIL";
    private static final String BY_ADDRESS = "ADDRESS";

    private final AuthLoginAttemptJpaRepository repository;
    private final AuthProperties properties;
    private final Clock clock;

    public RateLimitJdbcAdapter(AuthLoginAttemptJpaRepository repository, AuthProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean isLimited(AttemptPurpose purpose, String subjectEmail, String ipAddress) {
        boolean forReset = purpose == AttemptPurpose.PASSWORD_RESET;
        Instant since =
                clock.instant().minus(forReset ? properties.resetRateLimitWindow() : properties.rateLimitWindow());
        int perEmail = forReset ? properties.resetsPerEmail() : properties.failuresPerEmail();
        int perAddress = forReset ? properties.resetsPerAddress() : properties.failuresPerAddress();
        return countSince(purpose, BY_EMAIL, subjectEmail, since) >= perEmail
                || countSince(purpose, BY_ADDRESS, ipAddress, since) >= perAddress;
    }

    @Override
    public void record(AttemptPurpose purpose, String subjectEmail, String ipAddress) {
        Instant now = clock.instant();
        repository.save(new AuthLoginAttemptJpaEntity(UUID.randomUUID(), subjectEmail, BY_EMAIL, purpose.name(), now));
        repository.save(new AuthLoginAttemptJpaEntity(UUID.randomUUID(), ipAddress, BY_ADDRESS, purpose.name(), now));
    }

    private long countSince(AttemptPurpose purpose, String subjectKind, String subject, Instant since) {
        return repository.countByPurposeAndSubjectKindAndSubjectAndAttemptedAtAfter(
                purpose.name(), subjectKind, subject, since);
    }
}
