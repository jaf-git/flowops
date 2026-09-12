package com.flowops.workspace.infrastructure.persistence;

import com.flowops.workspace.application.shared.exception.InvitationLimitReachedException;
import com.flowops.workspace.application.shared.port.InvitationLimitPort;
import com.flowops.workspace.application.shared.port.LoadInvitationPort;
import com.flowops.workspace.application.shared.port.SaveInvitationPort;
import com.flowops.workspace.domain.enums.InvitationState;
import com.flowops.workspace.domain.enums.InvitedRole;
import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.Invitation;
import com.flowops.workspace.domain.model.InvitationId;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.WorkspaceId;
import com.flowops.workspace.infrastructure.persistence.entity.WorkspaceInvitationJpaEntity;
import com.flowops.workspace.infrastructure.persistence.repository.WorkspaceInvitationJpaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InvitationPersistenceAdapter implements LoadInvitationPort, SaveInvitationPort, InvitationLimitPort {
    private final WorkspaceInvitationJpaRepository invitations;
    private final Duration window;
    private final long perAddress;
    private final long perInviter;
    private final Clock clock;

    public InvitationPersistenceAdapter(
            WorkspaceInvitationJpaRepository invitations,
            @Value("${flowops.workspace.invitation.limit-window:P1D}") Duration window,
            @Value("${flowops.workspace.invitation.limit-per-address:3}") long perAddress,
            @Value("${flowops.workspace.invitation.limit-per-inviter:20}") long perInviter,
            Clock clock) {
        this.invitations = invitations;
        this.window = window;
        this.perAddress = perAddress;
        this.perInviter = perInviter;
        this.clock = clock;
    }

    @Override
    public Optional<Invitation> findByTokenHash(String tokenHash) {
        return invitations.findByTokenHash(tokenHash).map(this::toDomain);
    }

    @Override
    public Optional<Invitation> findOpenFor(EmailAddress email) {
        return invitations.findOpenFor(email.value()).map(this::toDomain);
    }

    @Override
    public Optional<Instant> findLastDeclineFor(EmailAddress email) {
        return invitations.findLastDeclineFor(email.value());
    }

    @Override
    public Invitation save(Invitation invitation) {
        invitations.save(new WorkspaceInvitationJpaEntity(
                invitation.id().value(),
                invitation.workspace().value(),
                invitation.email().value(),
                invitation.intendedRole().name(),
                invitation.intendedManager().value(),
                invitation.inviter().value(),
                invitation.state().name(),
                invitation.tokenHash(),
                invitation.expiresAt(),
                invitation.createdAt(),
                invitation.declinedAt()));
        return invitation;
    }

    @Override
    public void check(MembershipId inviter, EmailAddress invited) {
        Instant since = clock.instant().minus(window);

        if (invitations.countForAddressSince(invited.value(), since) >= perAddress) {
            throw new InvitationLimitReachedException(("that address has been invited %d times in the last %s, "
                            + "which is the limit; try again after that window passes")
                    .formatted(perAddress, humanised(window)));
        }
        if (invitations.countByInviterSince(inviter.value(), since) >= perInviter) {
            throw new InvitationLimitReachedException(("you have sent %d invitations in the last %s, which is "
                            + "the limit for one person; somebody else can invite, or try again after that "
                            + "window passes")
                    .formatted(perInviter, humanised(window)));
        }
    }

    private static String humanised(Duration window) {
        if (window.toHours() % 24 == 0 && window.toDays() >= 1) {
            return window.toDays() == 1 ? "day" : window.toDays() + " days";
        }
        return window.toHours() <= 1 ? "hour" : window.toHours() + " hours";
    }

    @Override
    public List<Invitation> listOpen() {
        return invitations.findOpen(clock.instant()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Invitation> findByIdForUpdate(InvitationId id) {
        return invitations.findByIdForUpdate(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<Invitation> findByTokenHashForUpdate(String tokenHash) {
        return invitations.findByTokenHashForUpdate(tokenHash).map(this::toDomain);
    }

    private Invitation toDomain(WorkspaceInvitationJpaEntity entity) {
        return new Invitation(
                InvitationId.of(entity.getId()),
                WorkspaceId.of(entity.getWorkspaceId()),
                EmailAddress.of(entity.getEmail()),
                InvitedRole.valueOf(entity.getIntendedRole()),
                MembershipId.of(entity.getIntendedManagerId()),
                MembershipId.of(entity.getInviterMembershipId()),
                InvitationState.valueOf(entity.getState()),
                entity.getTokenHash(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getDeclinedAt());
    }
}
