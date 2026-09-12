package com.flowops.process.infrastructure.workspace;

import com.flowops.process.application.shared.port.LoadPersonPort;
import com.flowops.process.application.shared.port.ReportingLinePort;
import com.flowops.process.application.shared.port.WorkspacePort;
import com.flowops.process.domain.model.PersonId;
import com.flowops.workspace.application.describedirectory.DescribeDirectoryUseCase;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProcessWorkspaceAdapter implements WorkspacePort, LoadPersonPort, ReportingLinePort {
    private final DescribeDirectoryUseCase describeDirectoryUseCase;

    public ProcessWorkspaceAdapter(DescribeDirectoryUseCase describeDirectoryUseCase) {
        this.describeDirectoryUseCase = describeDirectoryUseCase;
    }

    @Override
    public UUID currentWorkspaceId() {
        return describeDirectoryUseCase.currentWorkspaceId();
    }

    @Override
    public Optional<Person> describe(PersonId person) {
        return describeDirectoryUseCase.describe(person.value()).map(ProcessWorkspaceAdapter::translate);
    }

    @Override
    public List<Person> describeAll(Collection<PersonId> people) {
        List<UUID> ids = new ArrayList<>();
        for (PersonId person : people) {
            ids.add(person.value());
        }
        List<Person> described = new ArrayList<>();
        for (DescribeDirectoryUseCase.Person person : describeDirectoryUseCase.describeAll(ids)) {
            described.add(translate(person));
        }
        return described;
    }

    @Override
    public Set<PersonId> subtreeOf(PersonId actor) {
        Set<PersonId> subtree = new LinkedHashSet<>();
        for (UUID member : describeDirectoryUseCase.subtreeOf(actor.value())) {
            subtree.add(PersonId.of(member));
        }
        return subtree;
    }

    private static Person translate(DescribeDirectoryUseCase.Person person) {
        return new Person(PersonId.of(person.userId()), person.displayName(), person.active());
    }
}
