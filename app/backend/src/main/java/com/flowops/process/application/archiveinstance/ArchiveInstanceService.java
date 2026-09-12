package com.flowops.process.application.archiveinstance;

import com.flowops.process.application.shared.exception.NotAuthenticatedException;
import com.flowops.process.application.shared.port.AppendProcessEventPort;
import com.flowops.process.application.shared.port.IdentifyCallerPort;
import com.flowops.process.application.shared.port.SaveInstancePort;
import com.flowops.process.application.viewinstance.ViewInstanceUseCase;
import com.flowops.process.domain.enums.InstanceState;
import com.flowops.process.domain.enums.ProcessAction;
import com.flowops.process.domain.event.ProcessEvent;
import com.flowops.process.domain.exception.InstanceStillRunningException;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessInstance;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArchiveInstanceService implements ArchiveInstanceUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final SaveInstancePort saveInstancePort;
    private final ViewInstanceUseCase viewInstanceUseCase;
    private final AppendProcessEventPort appendProcessEventPort;
    private final Clock clock;

    public ArchiveInstanceService(
            IdentifyCallerPort identifyCallerPort,
            SaveInstancePort saveInstancePort,
            ViewInstanceUseCase viewInstanceUseCase,
            AppendProcessEventPort appendProcessEventPort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.saveInstancePort = saveInstancePort;
        this.viewInstanceUseCase = viewInstanceUseCase;
        this.appendProcessEventPort = appendProcessEventPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void archive(InstanceId instance) {
        Instant now = clock.instant();
        PersonId caller = caller();

        ProcessInstance found = viewInstanceUseCase.one(instance).instance();

        if (found.state() == InstanceState.RUNNING) {
            throw new InstanceStillRunningException();
        }

        saveInstancePort.archive(instance, now);
        appendProcessEventPort.append(ProcessEvent.onInstance(instance, ProcessAction.INSTANCE_ARCHIVED, caller, now));
    }

    @Override
    @Transactional
    public void restore(InstanceId instance) {
        Instant now = clock.instant();
        PersonId caller = caller();

        viewInstanceUseCase.one(instance);

        saveInstancePort.restore(instance);
        appendProcessEventPort.append(ProcessEvent.onInstance(instance, ProcessAction.INSTANCE_RESTORED, caller, now));
    }

    private PersonId caller() {
        return identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));
    }
}
