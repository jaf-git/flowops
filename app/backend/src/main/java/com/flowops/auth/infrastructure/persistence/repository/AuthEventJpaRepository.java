package com.flowops.auth.infrastructure.persistence.repository;

import com.flowops.auth.infrastructure.persistence.entity.AuthEventJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthEventJpaRepository extends JpaRepository<AuthEventJpaEntity, UUID> {
    @Modifying
    @Query("update AuthEventJpaEntity e set e.subjectEmail = null where e.subjectEmail = :address")
    void forgetSubjectAddress(@Param("address") String address);
}
