package com.flowops.process.application.closeinstance;

import com.flowops.process.application.shared.exception.InstanceNotFoundException;
import com.flowops.process.application.shared.exception.NotAuthenticatedException;
import com.flowops.process.application.shared.port.AppendProcessEventPort;
import com.flowops.process.application.shared.port.IdentifyCallerPort;
import com.flowops.process.application.shared.port.LoadInstancePort;
import com.flowops.process.application.shared.port.SaveInstancePort;
import com.flowops.process.domain.enums.ProcessAction;
import com.flowops.process.domain.event.ProcessEvent;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessInstance;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CloseInstanceService implements CloseInstanceUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadInstancePort loadInstancePort;
    private final SaveInstancePort saveInstancePort;
    private final AppendProcessEventPort appendProcessEventPort;
    private final Clock clock;

    public CloseInstanceService(
            IdentifyCallerPort identifyCallerPort,
            LoadInstancePort loadInstancePort,
            SaveInstancePort saveInstancePort,
            AppendProcessEventPort appendProcessEventPort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadInstancePort = loadInstancePort;
        this.saveInstancePort = saveInstancePort;
        this.appendProcessEventPort = appendProcessEventPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProcessInstance execute(InstanceId instance, String note) {
        Instant now = clock.instant();
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        ProcessInstance found = loadInstancePort.findById(instance).orElseThrow(InstanceNotFoundException::new);

        ProcessInstance closed = found.closedEarlyWith(note, now);
        saveInstancePort.closeEarly(closed);

        appendProcessEventPort.append(ProcessEvent.onInstance(instance, ProcessAction.INSTANCE_COMPLETED, caller, now));
        return closed;
    }
}
