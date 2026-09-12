package com.flowops.workspace.application.viewpeople;

import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.exception.SetupNotPermittedException;
import com.flowops.workspace.application.shared.port.CallerPermissionsPort;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.LoadInvitationPort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.domain.enums.MembershipStatus;
import com.flowops.workspace.domain.model.Invitation;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.PersonId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewPeopleService implements ViewPeopleUseCase {
    private static final String OWNER_ROLE = "OWNER";

    private final IdentifyCallerPort identifyCallerPort;
    private final LoadMembershipPort loadMembershipPort;
    private final LoadInvitationPort loadInvitationPort;
    private final DescribePeoplePort describePeoplePort;
    private final CallerPermissionsPort callerPermissionsPort;

    public ViewPeopleService(
            IdentifyCallerPort identifyCallerPort,
            LoadMembershipPort loadMembershipPort,
            LoadInvitationPort loadInvitationPort,
            DescribePeoplePort describePeoplePort,
            CallerPermissionsPort callerPermissionsPort) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadMembershipPort = loadMembershipPort;
        this.loadInvitationPort = loadInvitationPort;
        this.describePeoplePort = describePeoplePort;
        this.callerPermissionsPort = callerPermissionsPort;
    }

    @Override
    @Transactional(readOnly = true)
    public ViewPeopleResult execute() {
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(NotAuthenticatedException::new)
                .id();

        loadMembershipPort
                .findByPerson(caller)
                .filter(Membership::isActive)
                .orElseThrow(SetupNotPermittedException::new);

        List<Membership> memberships = loadMembershipPort.listAll();
        Map<PersonId, DescribePeoplePort.PersonDescription> described =
                describePeoplePort
                        .describe(memberships.stream().map(Membership::person).toList())
                        .stream()
                        .collect(Collectors.toMap(DescribePeoplePort.PersonDescription::id, Function.identity()));

        List<ViewPeopleResult.Person> people = memberships.stream()
                .filter(membership -> !membership.isErased())
                .filter(membership -> described.containsKey(membership.person()))
                .map(membership -> toPerson(membership, described.get(membership.person()), caller))
                .toList();

        long active = people.stream()
                .filter(person -> person.status() == MembershipStatus.ACTIVE)
                .count();

        return new ViewPeopleResult(people, invitationsFor(people, memberships), active <= 1);
    }

    private ViewPeopleResult.Person toPerson(
            Membership membership, DescribePeoplePort.PersonDescription described, PersonId caller) {
        return new ViewPeopleResult.Person(
                membership.id(),
                membership.person(),
                described.displayName(),
                described.role(),
                membership.managerOrRoot(),
                membership.status(),
                membership.deactivatedWhen(),
                membership.person().equals(caller));
    }

    private Optional<List<ViewPeopleResult.PendingInvitation>> invitationsFor(
            List<ViewPeopleResult.Person> people, List<Membership> memberships) {
        if (!callerPermissionsPort.callerHolds("PERSON_INVITE")) {
            return Optional.empty();
        }

        Optional<ViewPeopleResult.Person> viewer =
                people.stream().filter(ViewPeopleResult.Person::isSelf).findFirst();
        boolean seesEveryInvitation = viewer.map(ViewPeopleResult.Person::role)
                .filter(OWNER_ROLE::equals)
                .isPresent();
        Optional<MembershipId> viewerMembership = viewer.map(ViewPeopleResult.Person::membershipId);

        return Optional.of(loadInvitationPort.listOpen().stream()
                .filter(invitation -> seesEveryInvitation
                        || viewerMembership.filter(invitation.inviter()::equals).isPresent())
                .map(ViewPeopleService::toPendingInvitation)
                .toList());
    }

    private static ViewPeopleResult.PendingInvitation toPendingInvitation(Invitation invitation) {
        return new ViewPeopleResult.PendingInvitation(
                invitation.id(),
                invitation.email(),
                invitation.intendedRole(),
                invitation.intendedManager(),
                invitation.inviter(),
                invitation.state(),
                invitation.expiresAt());
    }
}
