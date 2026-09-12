package com.flowops.auth.infrastructure.persistence.repository;

import com.flowops.auth.infrastructure.persistence.entity.AuthPasswordResetTokenJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthPasswordResetTokenJpaRepository extends JpaRepository<AuthPasswordResetTokenJpaEntity, UUID> {
    Optional<AuthPasswordResetTokenJpaEntity> findByTokenHash(String tokenHash);
}
