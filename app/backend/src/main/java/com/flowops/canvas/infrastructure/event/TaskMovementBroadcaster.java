package com.flowops.canvas.infrastructure.event;

import com.flowops.canvas.application.stream.PublishCanvasDeltasUseCase;
import com.flowops.shared.event.TaskStateChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TaskMovementBroadcaster {
    private static final Logger LOG = LoggerFactory.getLogger(TaskMovementBroadcaster.class);

    private final PublishCanvasDeltasUseCase deltas;

    public TaskMovementBroadcaster(PublishCanvasDeltasUseCase deltas) {
        this.deltas = deltas;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskMoved(TaskStateChanged moved) {
        try {
            deltas.pump();
        } catch (RuntimeException broken) {
            LOG.warn("a canvas pump pass failed after commit; the next movement will retry it", broken);
        }
    }
}
