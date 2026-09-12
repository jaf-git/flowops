package com.flowops.process.application.addtask;

import com.flowops.process.application.shared.exception.NotAuthenticatedException;
import com.flowops.process.application.shared.port.CallerPermissionsPort;
import com.flowops.process.application.shared.port.IdentifyCallerPort;
import com.flowops.process.application.shared.port.LoadInstancePort;
import com.flowops.process.domain.enums.InstanceState;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessInstance;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewAttachableInstancesService implements ViewAttachableInstancesUseCase {
    private static final String EDITS_ANY_INSTANCE = "PROCESS_EDIT_INSTANCE";

    private final IdentifyCallerPort identifyCallerPort;
    private final CallerPermissionsPort callerPermissionsPort;
    private final LoadInstancePort loadInstancePort;

    public ViewAttachableInstancesService(
            IdentifyCallerPort identifyCallerPort,
            CallerPermissionsPort callerPermissionsPort,
            LoadInstancePort loadInstancePort) {
        this.identifyCallerPort = identifyCallerPort;
        this.callerPermissionsPort = callerPermissionsPort;
        this.loadInstancePort = loadInstancePort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Attachable> forSomebodyToChoose(Optional<UUID> preferring) {
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));
        boolean editsAnything = callerPermissionsPort.callerHolds(EDITS_ANY_INSTANCE);

        return loadInstancePort.findAll().stream()
                .filter(instance -> instance.state() == InstanceState.RUNNING)
                .filter(instance -> editsAnything || instance.owner().equals(caller))
                .sorted(Comparator.comparing(instance -> !alreadyWorkedInBy(instance, preferring.map(PersonId::of))))
                .map(instance -> new Attachable(instance.id(), instance.name()))
                .toList();
    }

    private boolean alreadyWorkedInBy(ProcessInstance instance, Optional<PersonId> person) {
        return person.map(who -> instance.heldByAnyOf(Set.of(who))).orElse(false);
    }
}
