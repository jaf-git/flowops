package com.flowops.process.infrastructure.auth;

import com.flowops.process.application.shared.port.IdentifyCallerPort;
import com.flowops.process.application.shared.port.LoadInstancePort;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.PersonId;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component("processOwnership")
public class ProcessOwnership {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadInstancePort loadInstancePort;

    public ProcessOwnership(IdentifyCallerPort identifyCallerPort, LoadInstancePort loadInstancePort) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadInstancePort = loadInstancePort;
    }

    public boolean ownsInstance(UUID instanceId) {
        if (instanceId == null) {
            return false;
        }
        PersonId caller = identifyCallerPort.currentCaller().orElse(null);
        if (caller == null) {
            return false;
        }
        return loadInstancePort
                .findById(InstanceId.of(instanceId))
                .map(instance -> instance.owner().equals(caller))
                .orElse(false);
    }
}
