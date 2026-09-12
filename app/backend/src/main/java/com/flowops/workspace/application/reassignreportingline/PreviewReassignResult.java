package com.flowops.workspace.application.reassignreportingline;

import com.flowops.workspace.domain.model.MembershipId;
import java.util.List;
import java.util.Optional;

public record PreviewReassignResult(
        String personName,
        Optional<String> formerManagerName,
        String newManagerName,
        List<MovingPerson> movingWithThem,
        boolean alreadyTheirManager) {
    public record MovingPerson(MembershipId membership, String displayName) {}
}
