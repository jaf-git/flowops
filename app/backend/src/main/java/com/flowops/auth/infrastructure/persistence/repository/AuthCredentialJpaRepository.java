package com.flowops.auth.infrastructure.persistence.repository;

import com.flowops.auth.infrastructure.persistence.entity.AuthCredentialJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthCredentialJpaRepository extends JpaRepository<AuthCredentialJpaEntity, UUID> {}
