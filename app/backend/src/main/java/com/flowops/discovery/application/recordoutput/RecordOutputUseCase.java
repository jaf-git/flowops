package com.flowops.discovery.application.recordoutput;

import com.flowops.discovery.domain.enums.OutputType;
import com.flowops.discovery.domain.enums.WorkNodeState;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.util.Optional;
import java.util.UUID;

public interface RecordOutputUseCase {
    Recorded execute(RecordOutput command);

    record RecordOutput(UUID nodeId, OutputType output) {}

    record Recorded(WorkNodeId node, WorkNodeState state, OutputType output, Optional<WorkNodeId> pairedWith) {}
}
