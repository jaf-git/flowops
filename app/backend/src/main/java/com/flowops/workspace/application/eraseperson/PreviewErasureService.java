package com.flowops.workspace.application.eraseperson;

import com.flowops.workspace.application.shared.exception.MembershipNotFoundException;
import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.port.DescribePeoplePort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.LoadMembershipPort;
import com.flowops.workspace.domain.model.Membership;
import com.flowops.workspace.domain.model.MembershipId;
import com.flowops.workspace.domain.model.PersonId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreviewErasureService implements PreviewErasureUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadMembershipPort loadMembershipPort;
    private final DescribePeoplePort describePeoplePort;

    public PreviewErasureService(
            IdentifyCallerPort identifyCallerPort,
            LoadMembershipPort loadMembershipPort,
            DescribePeoplePort describePeoplePort) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadMembershipPort = loadMembershipPort;
        this.describePeoplePort = describePeoplePort;
    }

    @Override
    @Transactional(readOnly = true)
    public PreviewErasureResult execute(PreviewErasureQuery query) {
        identifyCallerPort.currentCaller().orElseThrow(NotAuthenticatedException::new);

        List<Membership> everybody = loadMembershipPort.listAll();
        Membership subject = everybody.stream()
                .filter(membership -> membership.id().equals(query.person()))
                .findFirst()
                .orElseThrow(MembershipNotFoundException::new);

        Map<MembershipId, DescribePeoplePort.PersonDescription> described = describe(everybody);
        Optional<String> refusal = refusalFor(subject, everybody, described);

        return new PreviewErasureResult(
                subject.id(),
                Optional.ofNullable(described.get(subject.id())).map(DescribePeoplePort.PersonDescription::displayName),
                subject.deactivatedWhen(),
                refusal.isEmpty() && !subject.isErased(),
                refusal,
                PreviewErasureResult.DESTROYS,
                PreviewErasureResult.SURVIVES,
                subject.isErased());
    }

    private Optional<String> refusalFor(
            Membership subject,
            List<Membership> everybody,
            Map<MembershipId, DescribePeoplePort.PersonDescription> described) {
        if (subject.isErased()) {
            return Optional.empty();
        }
        if (subject.isActive()) {
            return Optional.of("SUBJECT_ACTIVE");
        }
        if (isTheLastOwner(subject, everybody, described)) {
            return Optional.of("ONLY_OWNER");
        }
        return Optional.empty();
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
