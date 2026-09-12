package com.flowops.process.application.shared.port;

import com.flowops.process.domain.model.PersonId;
import java.util.List;

public interface AssignablePeoplePort {
    List<Candidate> forCreator(PersonId creator);

    record Candidate(PersonId person, String displayName) {}
}
