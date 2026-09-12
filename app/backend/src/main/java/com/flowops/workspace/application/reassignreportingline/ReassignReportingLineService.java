package com.flowops.workspace.application.reassignreportingline;

import com.flowops.workspace.application.shared.exception.MembershipNotFoundException;
import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.LockWorkspaceStructurePort;
import com.flowops.workspace.application.shared.port.ReassignManagerPort;
import com.flowops.workspace.domain.event.WorkspaceEvent;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.PersonId;
import com.flowops.workspace.domain.model.ReportingTree;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReassignReportingLineService implements ReassignReportingLineUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LockWorkspaceStructurePort lockWorkspaceStructurePort;
    private final LoadMembershipPort loadMembershipPort;
    private final DescribePeoplePort describePeoplePort;
    private final ReassignManagerPort reassignManagerPort;
    private final AppendWorkspaceEventPort appendWorkspaceEventPort;
    private final LoadWorkspacePort loadWorkspacePort;
    private final Clock clock;

    public ReassignReportingLineService(
            IdentifyCallerPort identifyCallerPort,
            LockWorkspaceStructurePort lockWorkspaceStructurePort,
            LoadMembershipPort loadMembershipPort,
            DescribePeoplePort describePeoplePort,
            ReassignManagerPort reassignManagerPort,
            AppendWorkspaceEventPort appendWorkspaceEventPort,
            LoadWorkspacePort loadWorkspacePort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.lockWorkspaceStructurePort = lockWorkspaceStructurePort;
        this.loadMembershipPort = loadMembershipPort;
        this.describePeoplePort = describePeoplePort;
        this.reassignManagerPort = reassignManagerPort;
        this.appendWorkspaceEventPort = appendWorkspaceEventPort;
        this.loadWorkspacePort = loadWorkspacePort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReassignReportingLineResult execute(ReassignReportingLineCommand command) {
        Instant now = clock.instant();
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(NotAuthenticatedException::new)
                .id();

        lockWorkspaceStructurePort.lockForStructuralChange();

        ReportingTree tree = readTheTree();
        Membership subject = tree.find(command.person()).orElseThrow(MembershipNotFoundException::new);
        Membership newManager = tree.find(command.proposedManager()).orElseThrow(MembershipNotFoundException::new);

        Optional<Membership> moved = tree.reassign(command.person(), command.proposedManager());
        if (moved.isEmpty()) {
            return new ReassignReportingLineResult(
                    subject.id(), subject.managerOrRoot(), command.proposedManager(), false);
        }

        Membership formerManager =
                subject.managerOrRoot().flatMap(tree::find).orElseThrow(MembershipNotFoundException::new);

        reassignManagerPort.reassign(subject.id(), command.proposedManager());
        appendWorkspaceEventPort.append(WorkspaceEvent.reportingLineChanged(
                caller,
                subject.person(),
                formerManager.person(),
                newManager.person(),
                loadWorkspacePort.load().id(),
                now));

        return new ReassignReportingLineResult(subject.id(), subject.managerOrRoot(), command.proposedManager(), true);
    }

    private ReportingTree readTheTree() {
        List<Membership> memberships = loadMembershipPort.listAll();

        Map<PersonId, String> rolesByPerson = new HashMap<>();
        describePeoplePort
                .describe(memberships.stream().map(Membership::person).toList())
                .forEach(described -> rolesByPerson.put(described.id(), described.role()));

        Map<MembershipId, String> rolesByMembership = new HashMap<>();
        memberships.forEach(membership ->
                rolesByMembership.put(membership.id(), rolesByPerson.getOrDefault(membership.person(), "")));

        return ReportingTree.of(memberships, rolesByMembership);
    }
}
