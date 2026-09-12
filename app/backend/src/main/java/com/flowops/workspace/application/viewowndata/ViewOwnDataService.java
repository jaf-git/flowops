package com.flowops.workspace.application.viewowndata;

import com.flowops.workspace.application.shared.exception.MembershipNotFoundException;
import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.exception.SubjectNotDeactivatedException;
import com.flowops.workspace.application.shared.port.DataExportPort;
import com.flowops.workspace.application.shared.port.DescribeAccountPort;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.LoadConsentRecordPort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.application.shared.port.LoadWorkspaceEventsPort;
import com.flowops.workspace.domain.event.WorkspaceEvent;
import com.flowops.workspace.domain.model.ConsentRecord;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.PersonId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewOwnDataService implements ViewOwnDataUseCase {
    public static final Duration EXPORT_WINDOW = Duration.ofHours(24);

    public static final int EXPORT_LIMIT = 5;

    private final IdentifyCallerPort identifyCallerPort;
    private final LoadMembershipPort loadMembershipPort;
    private final DescribeAccountPort describeAccountPort;
    private final DescribePeoplePort describePeoplePort;
    private final LoadConsentRecordPort loadConsentRecordPort;
    private final LoadWorkspaceEventsPort loadWorkspaceEventsPort;
    private final DataExportPort dataExportPort;
    private final Clock clock;

    public ViewOwnDataService(
            IdentifyCallerPort identifyCallerPort,
            LoadMembershipPort loadMembershipPort,
            DescribeAccountPort describeAccountPort,
            DescribePeoplePort describePeoplePort,
            LoadConsentRecordPort loadConsentRecordPort,
            LoadWorkspaceEventsPort loadWorkspaceEventsPort,
            DataExportPort dataExportPort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadMembershipPort = loadMembershipPort;
        this.describeAccountPort = describeAccountPort;
        this.describePeoplePort = describePeoplePort;
        this.loadConsentRecordPort = loadConsentRecordPort;
        this.loadWorkspaceEventsPort = loadWorkspaceEventsPort;
        this.dataExportPort = dataExportPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ViewOwnDataResult execute(ViewOwnDataQuery query) {
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(NotAuthenticatedException::new)
                .id();

        List<Membership> everybody = loadMembershipPort.listAll();
        Membership subject = subjectOf(query, caller, everybody);

        DescribeAccountPort.Account account =
                describeAccountPort.describe(subject.person()).orElseThrow(MembershipNotFoundException::new);

        Map<MembershipId, String> names = namesOf(everybody);

        return new ViewOwnDataResult(
                account,
                subject.id(),
                subject.status(),
                subject.deactivatedWhen(),
                nameOfMembership(subject.manager(), names),
                reportingLineHistoryOf(subject, account, everybody, names),
                loadConsentRecordPort.newestFor(subject.person()).map(this::consent),
                ViewOwnDataResult.Authored.nothingYet(),
                dataExportPort.countProducedSince(
                        subject.person(), clock.instant().minus(EXPORT_WINDOW)),
                EXPORT_LIMIT,
                !subject.person().equals(caller));
    }

    private Membership subjectOf(ViewOwnDataQuery query, PersonId caller, List<Membership> everybody) {
        if (query.subject().isEmpty()) {
            return everybody.stream()
                    .filter(membership -> membership.person().equals(caller))
                    .findFirst()
                    .orElseThrow(MembershipNotFoundException::new);
        }

        Membership named = everybody.stream()
                .filter(membership -> membership.id().equals(query.subject().get()))
                .findFirst()
                .orElseThrow(MembershipNotFoundException::new);

        if (named.isActive()) {
            throw new SubjectNotDeactivatedException();
        }

        if (named.isErased()) {
            throw new MembershipNotFoundException();
        }
        return named;
    }

    private List<ViewOwnDataResult.ReportingLinePeriod> reportingLineHistoryOf(
            Membership subject,
            DescribeAccountPort.Account account,
            List<Membership> everybody,
            Map<MembershipId, String> names) {
        List<WorkspaceEvent> changes = loadWorkspaceEventsPort.reportingLineChangesFor(subject.person());
        List<ViewOwnDataResult.ReportingLinePeriod> periods = new ArrayList<>();

        Instant openedAt = account.createdAt();
        for (WorkspaceEvent change : changes) {
            periods.add(new ViewOwnDataResult.ReportingLinePeriod(
                    nameOfPerson(change.formerManager(), everybody, names),
                    openedAt,
                    Optional.of(change.occurredAt())));
            openedAt = change.occurredAt();
        }

        periods.add(new ViewOwnDataResult.ReportingLinePeriod(
                nameOfMembership(subject.manager(), names), openedAt, Optional.empty()));

        return periods.size() == 1 && periods.get(0).managerName().isEmpty() ? List.of() : periods;
    }

    private Optional<String> nameOfMembership(MembershipId membership, Map<MembershipId, String> names) {
        return membership == null ? Optional.empty() : Optional.ofNullable(names.get(membership));
    }

    private Optional<String> nameOfPerson(
            PersonId person, List<Membership> everybody, Map<MembershipId, String> names) {
        if (person == null) {
            return Optional.empty();
        }
        return everybody.stream()
                .filter(membership -> membership.person().equals(person))
                .findFirst()
                .map(Membership::id)
                .map(names::get);
    }

    private ViewOwnDataResult.Consent consent(ConsentRecord record) {
        return new ViewOwnDataResult.Consent(record.version(), record.language(), record.agreedAt());
    }

    private Map<MembershipId, String> namesOf(List<Membership> memberships) {
        Map<PersonId, MembershipId> membershipByPerson = new LinkedHashMap<>();
        memberships.forEach(membership -> membershipByPerson.put(membership.person(), membership.id()));

        Map<MembershipId, String> names = new LinkedHashMap<>();
        for (DescribePeoplePort.PersonDescription description :
                describePeoplePort.describe(membershipByPerson.keySet())) {
            MembershipId membership = membershipByPerson.get(description.id());
            if (membership != null) {
                names.put(membership, description.displayName());
            }
        }
        return names;
    }
}
