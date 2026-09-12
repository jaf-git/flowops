package com.flowops.workspace.infrastructure.persistence;

import com.flowops.workspace.application.shared.port.FindPersonByEmailPort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.application.shared.port.ReassignManagerPort;
import com.flowops.workspace.application.shared.port.SaveMembershipPort;
import com.flowops.workspace.domain.enums.MembershipStatus;
import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.PersonId;
import com.flowops.workspace.domain.model.WorkspaceId;
import com.flowops.workspace.infrastructure.persistence.entity.WorkspaceMembershipJpaEntity;
import com.flowops.workspace.infrastructure.persistence.repository.WorkspaceMembershipJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MembershipPersistenceAdapter implements LoadMembershipPort, SaveMembershipPort, ReassignManagerPort {
    private final WorkspaceMembershipJpaRepository memberships;
    private final FindPersonByEmailPort findPersonByEmailPort;
    private final Clock clock;

    public MembershipPersistenceAdapter(
            WorkspaceMembershipJpaRepository memberships, FindPersonByEmailPort findPersonByEmailPort, Clock clock) {
        this.memberships = memberships;
        this.findPersonByEmailPort = findPersonByEmailPort;
        this.clock = clock;
    }

    @Override
    public Optional<Membership> findByPerson(PersonId person) {
        return memberships.findByUserId(person.value()).map(this::toDomain);
    }

    @Override
    public Optional<Membership> findById(MembershipId id) {
        return memberships.findById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<Membership> findByEmail(EmailAddress email) {
        return findPersonByEmailPort.findPersonByEmail(email).flatMap(this::findByPerson);
    }

    @Override
    public List<Membership> listAll() {
        return memberships.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Membership save(WorkspaceId workspace, Membership membership) {
        memberships.save(new WorkspaceMembershipJpaEntity(
                membership.id().value(),
                workspace.value(),
                membership.person().value(),
                membership.status().name(),
                membership.manager() == null ? null : membership.manager().value(),
                clock.instant()));
        return membership;
    }

    @Override
    public void deactivate(MembershipId membership, Instant at) {
        memberships.deactivate(membership.value(), at);
    }

    @Override
    public void erase(MembershipId membership, Instant at) {
        memberships.erase(membership.value(), at);
    }

    @Override
    public void reassign(MembershipId membership, MembershipId newManager) {
        memberships.reassignManager(membership.value(), newManager.value());
    }

    private Membership toDomain(WorkspaceMembershipJpaEntity entity) {
        return new Membership(
                MembershipId.of(entity.getId()),
                PersonId.of(entity.getUserId()),
                MembershipStatus.valueOf(entity.getStatus()),
                entity.getManagerId() == null ? null : MembershipId.of(entity.getManagerId()),
                entity.getDeactivatedAt());
    }
}
