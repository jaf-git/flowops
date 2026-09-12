package com.flowops.task.infrastructure.workspace;

import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.ReportingLinePort;
import com.flowops.task.application.shared.port.WorkspacePort;
import com.flowops.task.domain.model.PersonId;
import com.flowops.workspace.application.describedirectory.DescribeDirectoryUseCase;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceDirectoryAdapter implements LoadPersonPort, ReportingLinePort, WorkspacePort {
    private final DescribeDirectoryUseCase describeDirectoryUseCase;

    public WorkspaceDirectoryAdapter(DescribeDirectoryUseCase describeDirectoryUseCase) {
        this.describeDirectoryUseCase = describeDirectoryUseCase;
    }

    @Override
    public UUID currentWorkspaceId() {
        return describeDirectoryUseCase.currentWorkspaceId();
    }

    @Override
    public Optional<Person> describe(PersonId person) {
        return describeDirectoryUseCase.describe(person.value()).map(WorkspaceDirectoryAdapter::toPerson);
    }

    @Override
    public List<Person> describeAll(Collection<PersonId> people) {
        return describeDirectoryUseCase
                .describeAll(people.stream().map(PersonId::value).toList())
                .stream()
                .map(WorkspaceDirectoryAdapter::toPerson)
                .toList();
    }

    @Override
    public boolean isWithinScopeOf(PersonId actor, PersonId subject) {
        return describeDirectoryUseCase.isWithinScopeOf(actor.value(), subject.value());
    }

    @Override
    public Set<PersonId> subtreeOf(PersonId actor) {
        return describeDirectoryUseCase.subtreeOf(actor.value()).stream()
                .map(PersonId::of)
                .collect(Collectors.toSet());
    }

    private static Person toPerson(DescribeDirectoryUseCase.Person described) {
        return new Person(PersonId.of(described.userId()), described.displayName(), described.active());
    }
}
