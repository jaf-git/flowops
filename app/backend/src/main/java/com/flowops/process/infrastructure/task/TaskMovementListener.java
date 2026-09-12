package com.flowops.process.infrastructure.task;

import com.flowops.process.application.advancegraph.AdvanceGraphUseCase;
import com.flowops.shared.event.TaskStateChanged;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TaskMovementListener {
    private final AdvanceGraphUseCase advanceGraphUseCase;

    public TaskMovementListener(AdvanceGraphUseCase advanceGraphUseCase) {
        this.advanceGraphUseCase = advanceGraphUseCase;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskMoved(TaskStateChanged moved) {
        advanceGraphUseCase.onTaskState(moved.task(), moved.state(), moved.assigned());
    }
}
