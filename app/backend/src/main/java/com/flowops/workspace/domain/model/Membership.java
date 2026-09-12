package com.flowops.workspace.domain.model;

import com.flowops.workspace.domain.enums.MembershipStatus;
import java.util.Objects;
import java.util.Optional;

public record Membership(
        MembershipId id,
        PersonId person,
        MembershipStatus status,
        MembershipId manager,
        java.time.Instant deactivatedAt) {
    public Membership {
        Objects.requireNonNull(id, "a membership identity is required");
        Objects.requireNonNull(person, "a membership belongs to a person");
        Objects.requireNonNull(status, "a membership has a status");
    }

    public Membership(MembershipId id, PersonId person, MembershipStatus status, MembershipId manager) {
        this(id, person, status, manager, null);
    }

    public java.util.Optional<java.time.Instant> deactivatedWhen() {
        return java.util.Optional.ofNullable(deactivatedAt);
    }

    public Optional<MembershipId> managerOrRoot() {
        return Optional.ofNullable(manager);
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }

    public boolean isErased() {
        return status == MembershipStatus.ERASED;
    }
}
