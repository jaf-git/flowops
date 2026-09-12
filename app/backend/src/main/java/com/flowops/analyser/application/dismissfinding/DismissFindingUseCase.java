package com.flowops.analyser.application.dismissfinding;

import java.util.UUID;

public interface DismissFindingUseCase {
    void execute(Dismiss command);

    record Dismiss(UUID findingId) {}

    class NoSuchFinding extends RuntimeException {
        public NoSuchFinding(UUID findingId) {
            super("no finding " + findingId + " to dismiss");
        }
    }
}
