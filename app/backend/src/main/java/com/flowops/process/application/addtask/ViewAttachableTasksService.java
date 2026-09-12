package com.flowops.process.application.addtask;

import com.flowops.process.application.shared.exception.InstanceNotFoundException;
import com.flowops.process.application.shared.port.AttachableTasksPort;
import com.flowops.process.application.shared.port.LoadInstancePort;
import com.flowops.process.domain.model.InstanceId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewAttachableTasksService implements ViewAttachableTasksUseCase {
    private final AttachableTasksPort attachableTasksPort;
    private final LoadInstancePort loadInstancePort;

    public ViewAttachableTasksService(AttachableTasksPort attachableTasksPort, LoadInstancePort loadInstancePort) {
        this.attachableTasksPort = attachableTasksPort;
        this.loadInstancePort = loadInstancePort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachableTasksPort.Attachable> forRun(InstanceId instance) {
        loadInstancePort.findById(instance).orElseThrow(InstanceNotFoundException::new);
        return attachableTasksPort.visible();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachableTasksPort.Attachable> forAStartingRun() {
        return attachableTasksPort.visible();
    }
}
