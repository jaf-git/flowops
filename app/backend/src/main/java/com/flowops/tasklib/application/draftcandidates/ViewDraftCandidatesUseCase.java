package com.flowops.tasklib.application.draftcandidates;

import com.flowops.tasklib.application.port.TaskTemplatePort;
import java.util.List;

public interface ViewDraftCandidatesUseCase {
    List<TaskTemplatePort.DraftCandidate> candidates();
}
