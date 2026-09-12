package com.flowops.tasklib.infrastructure.event;

import com.flowops.shared.event.FreeFormTaskCreated;
import com.flowops.tasklib.application.draftfromtask.DraftFromTaskUseCase;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class FreeFormTaskListener {
    private final DraftFromTaskUseCase draftFromTaskUseCase;

    public FreeFormTaskListener(DraftFromTaskUseCase draftFromTaskUseCase) {
        this.draftFromTaskUseCase = draftFromTaskUseCase;
    }

    @EventListener
    public void onFreeFormTaskCreated(FreeFormTaskCreated created) {
        draftFromTaskUseCase.draftFor(created.task());
    }
}
