package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.PersonId;
import java.util.Collection;
import java.util.List;

public interface DescribePeoplePort {
    List<PersonDescription> describe(Collection<PersonId> people);

    record PersonDescription(PersonId id, String displayName, String role) {}
}
