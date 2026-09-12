package com.flowops.auth.infrastructure.persistence.repository;

import com.flowops.auth.infrastructure.persistence.entity.AuthSessionMetadataJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionMetadataJpaRepository extends JpaRepository<AuthSessionMetadataJpaEntity, String> {
    List<AuthSessionMetadataJpaEntity> findByUserId(UUID userId);

    Optional<AuthSessionMetadataJpaEntity> findByReference(UUID reference);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AuthSessionMetadataJpaEntity m where m.userId = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);
}
