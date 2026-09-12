package com.flowops.auth.infrastructure.persistence.repository;

import com.flowops.auth.infrastructure.persistence.entity.AuthLoginAttemptJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface AuthLoginAttemptJpaRepository extends JpaRepository<AuthLoginAttemptJpaEntity, UUID> {
    long countByPurposeAndSubjectKindAndSubjectAndAttemptedAtAfter(
            String purpose, String subjectKind, String subject, Instant since);

    @Modifying
    void deleteBySubject(String subject);
}
