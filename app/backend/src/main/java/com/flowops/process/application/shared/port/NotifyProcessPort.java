package com.flowops.process.application.shared.port;

import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.StepId;

public interface NotifyProcessPort {
    void stepReachable(InstanceId instance, StepId step, PersonId processOwner);

    void instanceComplete(InstanceId instance, PersonId processOwner);
}
