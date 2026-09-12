package com.flowops.auth.infrastructure.persistence.repository;

import com.flowops.auth.infrastructure.persistence.entity.AuthSignupPasscodeJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSignupPasscodeJpaRepository extends JpaRepository<AuthSignupPasscodeJpaEntity, UUID> {
    Optional<AuthSignupPasscodeJpaEntity> findFirstByEmailOrderByIssuedAtDesc(String email);
}
