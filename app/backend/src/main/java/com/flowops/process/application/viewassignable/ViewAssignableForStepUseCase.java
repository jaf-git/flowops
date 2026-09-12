package com.flowops.process.application.viewassignable;

import com.flowops.process.application.shared.port.AssignablePeoplePort;
import com.flowops.process.domain.model.InstanceId;
import java.util.List;

public interface ViewAssignableForStepUseCase {
    List<AssignablePeoplePort.Candidate> execute(InstanceId instance);
}
