package com.flowops.chat.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation")
public class ConversationJpaEntity {
    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "participant_lo")
    private UUID participantLo;

    @Column(name = "participant_hi")
    private UUID participantHi;

    @Column(name = "team_manager_id")
    private UUID teamManagerId;

    @Column(name = "functional_role_id")
    private UUID functionalRoleId;

    @Column(name = "name")
    private String name;

    protected ConversationJpaEntity() {}

    public ConversationJpaEntity(
            UUID id,
            UUID workspaceId,
            String kind,
            Instant createdAt,
            UUID participantLo,
            UUID participantHi,
            UUID teamManagerId,
            UUID functionalRoleId,
            String name) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.kind = kind;
        this.createdAt = createdAt;
        this.participantLo = participantLo;
        this.participantHi = participantHi;
        this.teamManagerId = teamManagerId;
        this.functionalRoleId = functionalRoleId;
        this.name = name;
    }

    public UUID getFunctionalRoleId() {
        return functionalRoleId;
    }

    public String getName() {
        return name;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getKind() {
        return kind;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getParticipantLo() {
        return participantLo;
    }

    public UUID getParticipantHi() {
        return participantHi;
    }

    public UUID getTeamManagerId() {
        return teamManagerId;
    }
}
