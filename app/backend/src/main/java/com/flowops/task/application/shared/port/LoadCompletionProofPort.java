package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.CompletionProof;
import com.flowops.task.domain.model.TaskId;
import java.util.Optional;

public interface LoadCompletionProofPort {
    Optional<CompletionProof> findByTask(TaskId task);
}
