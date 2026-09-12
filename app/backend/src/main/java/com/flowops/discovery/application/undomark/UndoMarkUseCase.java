package com.flowops.discovery.application.undomark;

import java.util.UUID;

public interface UndoMarkUseCase {
    void execute(UUID nodeId);
}
