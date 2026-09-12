package com.flowops.process.infrastructure.task;

import com.flowops.process.application.shared.port.AssignablePeoplePort;
import com.flowops.process.domain.model.PersonId;
import com.flowops.task.application.viewassignablepeople.ViewAssignablePeopleUseCase;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AssignablePeopleAdapter implements AssignablePeoplePort {
    private final ViewAssignablePeopleUseCase viewAssignablePeopleUseCase;

    public AssignablePeopleAdapter(ViewAssignablePeopleUseCase viewAssignablePeopleUseCase) {
        this.viewAssignablePeopleUseCase = viewAssignablePeopleUseCase;
    }

    @Override
    public List<Candidate> forCreator(PersonId creator) {
        List<Candidate> candidates = new ArrayList<>();
        for (ViewAssignablePeopleUseCase.AssignablePerson person :
                viewAssignablePeopleUseCase.executeFor(creator.value())) {
            candidates.add(new Candidate(PersonId.of(person.id()), person.displayName()));
        }
        return candidates;
    }
}
