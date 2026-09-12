package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.PersonId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoadPersonPort {
    Optional<Person> describe(PersonId person);

    List<Person> describeAll(Collection<PersonId> people);

    record Person(PersonId id, String displayName, boolean active) {}
}
