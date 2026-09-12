package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.StateTransition;

public interface RecordTransitionPort {
    void record(StateTransition transition);
}
