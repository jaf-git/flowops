package com.flowops.nodepipeline.application.port;

import com.flowops.nodepipeline.domain.compose.DraftProcess;
import java.util.UUID;

public interface ProcessDraftPort {
    UUID save(DraftProcess draft);

    class UnresolvedDraftStepException extends RuntimeException {
        public UnresolvedDraftStepException(String message) {
            super(message);
        }
    }
}
