package com.flowops.workspace.infrastructure.persistence.repository;

import com.flowops.workspace.infrastructure.persistence.entity.WorkspaceInvitationJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceInvitationJpaRepository extends JpaRepository<WorkspaceInvitationJpaEntity, UUID> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "update WorkspaceInvitationJpaEntity i set i.email = :opaque where lower(i.email) = lower(:real)")
    void replaceAddress(
            @org.springframework.data.repository.query.Param("real") String realAddress,
            @org.springframework.data.repository.query.Param("opaque") String opaqueAddress);

    @Query("select i from WorkspaceInvitationJpaEntity i where lower(i.email) = lower(:email) "
            + "and i.state in ('AWAITING_APPROVAL', 'SENT')")
    Optional<WorkspaceInvitationJpaEntity> findOpenFor(@Param("email") String email);

    @Query("select max(i.declinedAt) from WorkspaceInvitationJpaEntity i "
            + "where lower(i.email) = lower(:email) and i.declinedAt is not null")
    Optional<Instant> findLastDeclineFor(@Param("email") String email);

    @Query("select count(i) from WorkspaceInvitationJpaEntity i "
            + "where lower(i.email) = lower(:email) and i.createdAt > :since")
    long countForAddressSince(@Param("email") String email, @Param("since") Instant since);

    @Query("select count(i) from WorkspaceInvitationJpaEntity i "
            + "where i.inviterMembershipId = :inviter and i.createdAt > :since")
    long countByInviterSince(@Param("inviter") UUID inviter, @Param("since") Instant since);

    List<WorkspaceInvitationJpaEntity> findByInviterMembershipId(UUID inviterMembershipId);

    @Query("select i from WorkspaceInvitationJpaEntity i where i.state in ('AWAITING_APPROVAL', 'SENT') "
            + "and i.expiresAt > :now order by i.createdAt")
    List<WorkspaceInvitationJpaEntity> findOpen(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from WorkspaceInvitationJpaEntity i where i.id = :id")
    Optional<WorkspaceInvitationJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<WorkspaceInvitationJpaEntity> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from WorkspaceInvitationJpaEntity i where i.tokenHash = :tokenHash")
    Optional<WorkspaceInvitationJpaEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
