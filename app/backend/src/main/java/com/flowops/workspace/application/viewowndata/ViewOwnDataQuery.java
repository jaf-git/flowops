package com.flowops.workspace.application.viewowndata;

import com.flowops.workspace.domain.model.MembershipId;
import java.util.Optional;

public record ViewOwnDataQuery(Optional<MembershipId> subject) {
    public static ViewOwnDataQuery forTheCaller() {
        return new ViewOwnDataQuery(Optional.empty());
    }

    public static ViewOwnDataQuery onBehalfOf(MembershipId subject) {
        return new ViewOwnDataQuery(Optional.of(subject));
    }
}
