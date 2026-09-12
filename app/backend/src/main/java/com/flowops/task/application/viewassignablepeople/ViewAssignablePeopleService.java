package com.flowops.task.application.viewassignablepeople;

import com.flowops.task.application.shared.exception.NotAuthenticatedException;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.ReportingLinePort;
import com.flowops.task.domain.model.PersonId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewAssignablePeopleService implements ViewAssignablePeopleUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final ReportingLinePort reportingLinePort;
    private final LoadPersonPort loadPersonPort;

    public ViewAssignablePeopleService(
            IdentifyCallerPort identifyCallerPort, ReportingLinePort reportingLinePort, LoadPersonPort loadPersonPort) {
        this.identifyCallerPort = identifyCallerPort;
        this.reportingLinePort = reportingLinePort;
        this.loadPersonPort = loadPersonPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignablePerson> execute() {
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));
        return listFor(caller);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignablePerson> executeFor(java.util.UUID creator) {
        return listFor(PersonId.of(creator));
    }

    private List<AssignablePerson> listFor(PersonId creator) {
        Set<PersonId> candidates = new LinkedHashSet<>();
        candidates.add(creator);
        candidates.addAll(reportingLinePort.subtreeOf(creator));

        return loadPersonPort.describeAll(candidates).stream()
                .filter(LoadPersonPort.Person::active)
                .map(person -> new AssignablePerson(person.id().value(), person.displayName()))
                .toList();
    }
}
