package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.CompletionProof;

public interface SaveCompletionProofPort {
    void save(CompletionProof proof);
}
