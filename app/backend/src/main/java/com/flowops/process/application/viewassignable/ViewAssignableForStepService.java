package com.flowops.process.application.viewassignable;

import com.flowops.process.application.shared.exception.InstanceNotFoundException;
import com.flowops.process.application.shared.port.AssignablePeoplePort;
import com.flowops.process.application.shared.port.LoadInstancePort;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.ProcessInstance;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewAssignableForStepService implements ViewAssignableForStepUseCase {
    private final LoadInstancePort loadInstancePort;
    private final AssignablePeoplePort assignablePeoplePort;

    public ViewAssignableForStepService(LoadInstancePort loadInstancePort, AssignablePeoplePort assignablePeoplePort) {
        this.loadInstancePort = loadInstancePort;
        this.assignablePeoplePort = assignablePeoplePort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignablePeoplePort.Candidate> execute(InstanceId instance) {
        ProcessInstance run = loadInstancePort.findById(instance).orElseThrow(InstanceNotFoundException::new);
        return assignablePeoplePort.forCreator(run.startedBy());
    }
}
