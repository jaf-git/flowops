package com.flowops.tasklib.application.draftfromtask;

import java.util.UUID;

public interface DraftFromTaskUseCase {
    void draftFor(UUID task);
}
