package com.flowops.auth.domain.model;

import com.flowops.auth.domain.enums.AccountState;
import com.flowops.auth.domain.enums.LandingTarget;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class User {
    private final UserId id;
    private final EmailAddress email;
    private final AccountState accountState;
    private final RoleName role;
    private final Instant createdAt;
    private final boolean setupCompleted;
    private final DisplayName displayName;

    private User(
            UserId id,
            EmailAddress email,
            AccountState accountState,
            RoleName role,
            Instant createdAt,
            boolean setupCompleted,
            DisplayName displayName) {
        this.id = Objects.requireNonNull(id);
        this.email = Objects.requireNonNull(email);
        this.accountState = Objects.requireNonNull(accountState);
        this.role = Objects.requireNonNull(role);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.setupCompleted = setupCompleted;
        this.displayName = displayName;
    }

    public static User registerOwner(EmailAddress email, Instant createdAt) {
        return new User(UserId.generate(), email, AccountState.ACTIVE, RoleName.OWNER, createdAt, false, null);
    }

    public static User registerInvited(EmailAddress email, DisplayName displayName, RoleName role, Instant createdAt) {
        return new User(UserId.generate(), email, AccountState.ACTIVE, role, createdAt, false, displayName);
    }

    public static User rebuild(
            UserId id,
            EmailAddress email,
            AccountState accountState,
            RoleName role,
            Instant createdAt,
            boolean setupCompleted,
            DisplayName displayName) {
        return new User(id, email, accountState, role, createdAt, setupCompleted, displayName);
    }

    public User completeSetup() {
        return new User(id, email, accountState, role, createdAt, true, displayName);
    }

    public boolean setupCompleted() {
        return setupCompleted;
    }

    public User withDisplayName(DisplayName displayName) {
        return new User(id, email, accountState, role, createdAt, setupCompleted, Objects.requireNonNull(displayName));
    }

    public User anonymised(EmailAddress opaqueAddress) {
        return new User(
                id, Objects.requireNonNull(opaqueAddress), AccountState.ERASED, role, createdAt, setupCompleted, null);
    }

    public Optional<DisplayName> displayName() {
        return Optional.ofNullable(displayName);
    }

    public boolean canAuthenticate() {
        return accountState.permitsAuthentication();
    }

    public LandingTarget landingTarget() {
        if (role.ownsWorkspaceSetup() && !setupCompleted) {
            return LandingTarget.WORKSPACE_SETUP;
        }
        return role.landingTarget();
    }

    public UserId id() {
        return id;
    }

    public EmailAddress email() {
        return email;
    }

    public AccountState accountState() {
        return accountState;
    }

    public RoleName role() {
        return role;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
