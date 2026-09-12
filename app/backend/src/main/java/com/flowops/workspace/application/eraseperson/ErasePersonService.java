package com.flowops.workspace.application.eraseperson;

import com.flowops.workspace.application.shared.exception.ConfirmationNameMismatchException;
import com.flowops.workspace.application.shared.exception.MembershipNotFoundException;
import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.exception.OnlyOwnerException;
import com.flowops.workspace.application.shared.exception.SubjectNotDeactivatedException;
import com.flowops.workspace.application.shared.port.AnonymisePersonPort;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import com.flowops.workspace.application.shared.port.DescribeAccountPort;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.EraseInvitationTracesPort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.LockWorkspaceStructurePort;
import com.flowops.workspace.application.shared.port.RequireReauthenticationPort;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ErasePersonService implements ErasePersonUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final RequireReauthenticationPort requireReauthenticationPort;
    private final LockWorkspaceStructurePort lockWorkspaceStructurePort;
    private final LoadMembershipPort loadMembershipPort;
    private final DescribePeoplePort describePeoplePort;
    private final SaveMembershipPort saveMembershipPort;
    private final AnonymisePersonPort anonymisePersonPort;
    private final DescribeAccountPort describeAccountPort;
    private final EraseInvitationTracesPort eraseInvitationTracesPort;
    private final AppendWorkspaceEventPort appendWorkspaceEventPort;
    private final LoadWorkspacePort loadWorkspacePort;
    private final Clock clock;

    public ErasePersonService(
            IdentifyCallerPort identifyCallerPort,
            RequireReauthenticationPort requireReauthenticationPort,
            LockWorkspaceStructurePort lockWorkspaceStructurePort,
            LoadMembershipPort loadMembershipPort,
            DescribePeoplePort describePeoplePort,
            SaveMembershipPort saveMembershipPort,
            AnonymisePersonPort anonymisePersonPort,
            DescribeAccountPort describeAccountPort,
            EraseInvitationTracesPort eraseInvitationTracesPort,
            AppendWorkspaceEventPort appendWorkspaceEventPort,
            LoadWorkspacePort loadWorkspacePort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.requireReauthenticationPort = requireReauthenticationPort;
        this.lockWorkspaceStructurePort = lockWorkspaceStructurePort;
        this.loadMembershipPort = loadMembershipPort;
        this.describePeoplePort = describePeoplePort;
        this.saveMembershipPort = saveMembershipPort;
        this.anonymisePersonPort = anonymisePersonPort;
        this.describeAccountPort = describeAccountPort;
        this.eraseInvitationTracesPort = eraseInvitationTracesPort;
        this.appendWorkspaceEventPort = appendWorkspaceEventPort;
        this.loadWorkspacePort = loadWorkspacePort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ErasePersonResult execute(ErasePersonCommand command) {
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

        if (subject.isErased()) {
            return new ErasePersonResult(subject.id(), subject.person(), false);
        }

        if (subject.isActive()) {
            throw new SubjectNotDeactivatedException();
        }

        Map<MembershipId, DescribePeoplePort.PersonDescription> described = describe(everybody);

        if (isTheLastOwner(subject, everybody, described)) {
            throw new OnlyOwnerException();
        }

        requireReauthenticationPort.requireRecentReauthentication();

        requireTheNameMatches(
                command.typedName(),
                Optional.ofNullable(described.get(subject.id()))
                        .map(DescribePeoplePort.PersonDescription::displayName));

        WorkspaceId workspace = loadWorkspacePort.load().id();

        String addressBeingDestroyed = describeAccountPort
                .describe(subject.person())
                .map(DescribeAccountPort.Account::emailAddress)
                .orElseThrow(MembershipNotFoundException::new);

        anonymisePersonPort.anonymise(subject.person());

        eraseInvitationTracesPort.replaceAddress(
                addressBeingDestroyed,
                describeAccountPort
                        .describe(subject.person())
                        .map(DescribeAccountPort.Account::emailAddress)
                        .orElseThrow(MembershipNotFoundException::new));

        saveMembershipPort.erase(subject.id(), now);

        appendWorkspaceEventPort.append(WorkspaceEvent.personErased(caller, subject.person(), workspace, now));

        return new ErasePersonResult(subject.id(), subject.person(), true);
    }

    private void requireTheNameMatches(String typed, Optional<String> held) {
        String expected = held.orElseThrow(ConfirmationNameMismatchException::new);
        if (typed == null || !typed.trim().equalsIgnoreCase(expected.trim())) {
            throw new ConfirmationNameMismatchException();
        }
    }

    private boolean isTheLastOwner(
            Membership subject,
            List<Membership> everybody,
            Map<MembershipId, DescribePeoplePort.PersonDescription> described) {
        if (!isOwner(subject.id(), described)) {
            return false;
        }
        return everybody.stream()
                .filter(membership -> !membership.id().equals(subject.id()))
                .filter(membership -> !membership.isErased())
                .noneMatch(membership -> isOwner(membership.id(), described));
    }

    private boolean isOwner(
            MembershipId membership, Map<MembershipId, DescribePeoplePort.PersonDescription> described) {
        DescribePeoplePort.PersonDescription description = described.get(membership);
        return description != null && "OWNER".equals(description.role().toUpperCase(Locale.ROOT));
    }

    private Map<MembershipId, DescribePeoplePort.PersonDescription> describe(List<Membership> memberships) {
        Map<PersonId, MembershipId> membershipByPerson = new LinkedHashMap<>();
        memberships.forEach(membership -> membershipByPerson.put(membership.person(), membership.id()));

        Map<MembershipId, DescribePeoplePort.PersonDescription> described = new LinkedHashMap<>();
        for (DescribePeoplePort.PersonDescription description :
                describePeoplePort.describe(membershipByPerson.keySet())) {
            MembershipId membership = membershipByPerson.get(description.id());
            if (membership != null) {
                described.put(membership, description);
            }
        }
        return described;
    }
}
