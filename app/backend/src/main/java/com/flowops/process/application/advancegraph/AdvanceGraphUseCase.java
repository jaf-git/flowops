package com.flowops.process.application.advancegraph;

import java.util.UUID;

public interface AdvanceGraphUseCase {
    void onTaskState(UUID task, String state, boolean assigned);
}
