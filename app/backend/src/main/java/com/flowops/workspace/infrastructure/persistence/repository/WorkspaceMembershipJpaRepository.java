package com.flowops.workspace.infrastructure.persistence.repository;

import com.flowops.workspace.infrastructure.persistence.entity.WorkspaceMembershipJpaEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceMembershipJpaRepository extends JpaRepository<WorkspaceMembershipJpaEntity, UUID> {
    Optional<WorkspaceMembershipJpaEntity> findByUserId(UUID userId);

    @Modifying
    @Query("update WorkspaceMembershipJpaEntity m set m.managerId = :manager where m.id = :membership")
    void reassignManager(@Param("membership") UUID membership, @Param("manager") UUID manager);

    @Modifying
    @Query("update WorkspaceMembershipJpaEntity m set m.functionalRoleId = :role where m.id = :membership")
    void assignFunctionalRole(@Param("membership") UUID membership, @Param("role") UUID role);

    long countByFunctionalRoleId(UUID functionalRoleId);

    @Modifying
    @Query("update WorkspaceMembershipJpaEntity m set m.status = 'DEACTIVATED', m.deactivatedAt = :at"
            + " where m.id = :membership")
    void deactivate(@Param("membership") UUID membership, @Param("at") Instant at);

    @Modifying
    @Query("update WorkspaceMembershipJpaEntity m set m.status = 'ERASED', m.erasedAt = :at where m.id = :membership")
    void erase(@Param("membership") UUID membership, @Param("at") Instant at);
}
