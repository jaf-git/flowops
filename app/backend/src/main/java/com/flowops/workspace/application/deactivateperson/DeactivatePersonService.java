package com.flowops.workspace.application.deactivateperson;

import com.flowops.workspace.application.shared.exception.MembershipNotFoundException;
import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.exception.OnlyOwnerException;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.EndPersonSessionsPort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.LockWorkspaceStructurePort;
import com.flowops.workspace.application.shared.port.ReassignManagerPort;
import com.flowops.workspace.application.shared.port.SaveMembershipPort;
import com.flowops.workspace.domain.event.WorkspaceEvent;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.PersonId;
import com.flowops.workspace.domain.model.WorkspaceId;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeactivatePersonService implements DeactivatePersonUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LockWorkspaceStructurePort lockWorkspaceStructurePort;
    private final LoadMembershipPort loadMembershipPort;
    private final DescribePeoplePort describePeoplePort;
    private final SaveMembershipPort saveMembershipPort;
    private final ReassignManagerPort reassignManagerPort;
    private final EndPersonSessionsPort endPersonSessionsPort;
    private final AppendWorkspaceEventPort appendWorkspaceEventPort;
    private final LoadWorkspacePort loadWorkspacePort;
    private final Clock clock;

    public DeactivatePersonService(
            IdentifyCallerPort identifyCallerPort,
            LockWorkspaceStructurePort lockWorkspaceStructurePort,
            LoadMembershipPort loadMembershipPort,
            DescribePeoplePort describePeoplePort,
            SaveMembershipPort saveMembershipPort,
            ReassignManagerPort reassignManagerPort,
            EndPersonSessionsPort endPersonSessionsPort,
            AppendWorkspaceEventPort appendWorkspaceEventPort,
            LoadWorkspacePort loadWorkspacePort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.lockWorkspaceStructurePort = lockWorkspaceStructurePort;
        this.loadMembershipPort = loadMembershipPort;
        this.describePeoplePort = describePeoplePort;
        this.saveMembershipPort = saveMembershipPort;
        this.reassignManagerPort = reassignManagerPort;
        this.endPersonSessionsPort = endPersonSessionsPort;
        this.appendWorkspaceEventPort = appendWorkspaceEventPort;
        this.loadWorkspacePort = loadWorkspacePort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DeactivatePersonResult execute(DeactivatePersonCommand command) {
        Instant now = clock.instant();
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(NotAuthenticatedException::new)
                .id();

        lockWorkspaceStructurePort.lockForStructuralChange();

        List<Membership> everybody = loadMembershipPort.listAll();
        Membership subject = everybody.stream()
                .filter(membership -> membership.id().equals(command.person()))
                .findFirst()
                .orElseThrow(MembershipNotFoundException::new);

        if (!subject.isActive()) {
            return new DeactivatePersonResult(subject.id(), false, List.of(), Optional.empty());
        }

        Map<MembershipId, String> roles = rolesOf(everybody);

        if (isTheLastActiveOwner(subject, everybody, roles)) {
            throw new OnlyOwnerException();
        }

        WorkspaceId workspace = loadWorkspacePort.load().id();

        List<Membership> directReports = everybody.stream()
                .filter(Membership::isActive)
                .filter(membership -> command.person().equals(membership.manager()))
                .toList();

        Optional<MembershipId> newManager = directReports.isEmpty() ? Optional.empty() : subject.managerOrRoot();

        saveMembershipPort.deactivate(subject.id(), now);

        newManager.ifPresent(
                manager -> directReports.forEach(report -> reassignManagerPort.reassign(report.id(), manager)));

        endPersonSessionsPort.endEverySessionFor(subject.person());

        appendWorkspaceEventPort.append(WorkspaceEvent.personDeactivated(
                caller,
                subject.person(),
                newManager.flatMap(manager -> personBehind(manager, everybody)).orElse(null),
                workspace,
                now));

        return new DeactivatePersonResult(
                subject.id(), true, directReports.stream().map(Membership::id).toList(), newManager);
    }

    private boolean isTheLastActiveOwner(
            Membership subject, List<Membership> everybody, Map<MembershipId, String> roles) {
        if (!"OWNER".equals(roles.get(subject.id()))) {
            return false;
        }
        return everybody.stream()
                .filter(Membership::isActive)
                .filter(membership -> !membership.id().equals(subject.id()))
                .noneMatch(membership -> "OWNER".equals(roles.get(membership.id())));
    }

    private Map<MembershipId, String> rolesOf(List<Membership> memberships) {
        Map<PersonId, MembershipId> membershipByPerson = new LinkedHashMap<>();
        memberships.forEach(membership -> membershipByPerson.put(membership.person(), membership.id()));

        Map<MembershipId, String> roles = new LinkedHashMap<>();
        for (DescribePeoplePort.PersonDescription description :
                describePeoplePort.describe(membershipByPerson.keySet())) {
            MembershipId membership = membershipByPerson.get(description.id());
            if (membership != null) {
                roles.put(membership, description.role());
            }
        }
        return roles;
    }

    private Optional<PersonId> personBehind(MembershipId membership, List<Membership> everybody) {
        return everybody.stream()
                .filter(candidate -> candidate.id().equals(membership))
                .map(Membership::person)
                .findFirst();
    }
}
