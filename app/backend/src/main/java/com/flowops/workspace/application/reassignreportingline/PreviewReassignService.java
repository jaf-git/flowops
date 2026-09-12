package com.flowops.workspace.application.reassignreportingline;

import com.flowops.workspace.application.shared.exception.MembershipNotFoundException;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.PersonId;
import com.flowops.workspace.domain.model.ReportingTree;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreviewReassignService implements PreviewReassignUseCase {
    private final LoadMembershipPort loadMembershipPort;
    private final DescribePeoplePort describePeoplePort;

    public PreviewReassignService(LoadMembershipPort loadMembershipPort, DescribePeoplePort describePeoplePort) {
        this.loadMembershipPort = loadMembershipPort;
        this.describePeoplePort = describePeoplePort;
    }

    @Override
    @Transactional(readOnly = true)
    public PreviewReassignResult execute(PreviewReassignQuery query) {
        List<Membership> memberships = loadMembershipPort.listAll();
        Map<PersonId, DescribePeoplePort.PersonDescription> described = new HashMap<>();
        describePeoplePort
                .describe(memberships.stream().map(Membership::person).toList())
                .forEach(person -> described.put(person.id(), person));

        Map<MembershipId, String> roles = new HashMap<>();
        Map<MembershipId, String> names = new HashMap<>();
        for (Membership membership : memberships) {
            DescribePeoplePort.PersonDescription person = described.get(membership.person());
            roles.put(membership.id(), person == null ? "" : person.role());
            names.put(membership.id(), person == null ? "" : person.displayName());
        }

        ReportingTree tree = ReportingTree.of(memberships, roles);
        Membership subject = tree.find(query.person()).orElseThrow(MembershipNotFoundException::new);
        tree.find(query.proposedManager()).orElseThrow(MembershipNotFoundException::new);

        List<PreviewReassignResult.MovingPerson> moving = tree.subtreeOf(query.person()).stream()
                .map(member -> new PreviewReassignResult.MovingPerson(member.id(), names.getOrDefault(member.id(), "")))
                .toList();

        return new PreviewReassignResult(
                names.getOrDefault(subject.id(), ""),
                subject.managerOrRoot().map(manager -> names.getOrDefault(manager, "")),
                names.getOrDefault(query.proposedManager(), ""),
                moving,
                query.proposedManager().equals(subject.manager()));
    }
}
