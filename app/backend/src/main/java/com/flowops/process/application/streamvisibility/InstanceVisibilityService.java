package com.flowops.process.application.streamvisibility;

import com.flowops.process.application.shared.port.LoadInstancePort;
import com.flowops.process.application.shared.port.ReportingLinePort;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessInstance;
import com.flowops.process.domain.model.TaskRef;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstanceVisibilityService implements InstanceVisibilityUseCase {
    private static final String SEES_EVERY_INSTANCE = "PROCESS_VIEW_ANY";
    private static final String SEES_THE_SUBTREE = "PROCESS_VIEW_SUBTREE";
    private static final String SEES_OWN = "PROCESS_VIEW_OWN";

    private final LoadInstancePort loadInstancePort;
    private final ReportingLinePort reportingLinePort;

    public InstanceVisibilityService(LoadInstancePort loadInstancePort, ReportingLinePort reportingLinePort) {
        this.loadInstancePort = loadInstancePort;
        this.reportingLinePort = reportingLinePort;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> instanceOf(UUID task) {
        return loadInstancePort.findByTask(TaskRef.of(task)).map(instance -> instance.id()
                .value());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean mayView(UUID person, Set<String> permissions, UUID instance) {
        if (!permissions.contains(SEES_OWN)
                && !permissions.contains(SEES_THE_SUBTREE)
                && !permissions.contains(SEES_EVERY_INSTANCE)) {
            return false;
        }

        Optional<ProcessInstance> found = loadInstancePort.findById(InstanceId.of(instance));
        if (found.isEmpty()) {
            return false;
        }
        if (permissions.contains(SEES_EVERY_INSTANCE)) {
            return true;
        }

        ProcessInstance run = found.get();
        Set<PersonId> scope = peopleInScope(PersonId.of(person), permissions);
        return scope.contains(run.owner()) || run.heldByAnyOf(scope);
    }

    private Set<PersonId> peopleInScope(PersonId person, Set<String> permissions) {
        Set<PersonId> scope = new LinkedHashSet<>();
        scope.add(person);
        if (permissions.contains(SEES_THE_SUBTREE)) {
            scope.addAll(reportingLinePort.subtreeOf(person));
        }
        return scope;
    }
}
