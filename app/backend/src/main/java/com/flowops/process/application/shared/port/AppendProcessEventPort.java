package com.flowops.process.application.shared.port;

import com.flowops.process.domain.event.ProcessEvent;

public interface AppendProcessEventPort {
    void append(ProcessEvent event);
}
