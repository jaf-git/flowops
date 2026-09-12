package com.flowops.process.application.addtask;

import com.flowops.process.application.shared.port.AttachableTasksPort;
import com.flowops.process.domain.model.InstanceId;
import java.util.List;

public interface ViewAttachableTasksUseCase {
    List<AttachableTasksPort.Attachable> forRun(InstanceId instance);

    List<AttachableTasksPort.Attachable> forAStartingRun();
}
