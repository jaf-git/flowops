package com.flowops.workspace.application.deactivateperson;

import com.flowops.workspace.domain.model.MembershipId;
import java.util.List;
import java.util.Optional;

public record DeactivatePersonResult(
        MembershipId person, boolean changed, List<MembershipId> reportsMoved, Optional<MembershipId> reportsMovedTo) {
    public DeactivatePersonResult {
        reportsMoved = List.copyOf(reportsMoved);
    }
}
