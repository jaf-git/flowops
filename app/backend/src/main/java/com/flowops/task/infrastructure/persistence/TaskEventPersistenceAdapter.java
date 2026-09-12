package com.flowops.task.infrastructure.persistence;

import com.flowops.task.application.shared.port.AppendTaskEventPort;
import com.flowops.task.application.shared.port.ReadTaskEventFeedPort;
import com.flowops.task.application.streamevents.TaskEventFeedUseCase.TaskEventRecord;
import com.flowops.task.domain.event.TaskEvent;
import com.flowops.task.infrastructure.persistence.entity.TaskEventJpaEntity;
import com.flowops.task.infrastructure.persistence.repository.TaskEventJpaRepository;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

@Component
public class TaskEventPersistenceAdapter implements AppendTaskEventPort, ReadTaskEventFeedPort {
    private final TaskEventJpaRepository events;

    public TaskEventPersistenceAdapter(TaskEventJpaRepository events) {
        this.events = events;
    }

    @Override
    public void append(TaskEvent event) {
        events.save(new TaskEventJpaEntity(
                event.id(),
                event.task().value(),
                event.action().name(),
                event.actor().value(),
                event.occurredAt()));
    }

    @Override
    public long latestSequence() {
        return events.highestSequence();
    }

    @Override
    public List<TaskEventRecord> after(long cursor, int limit) {
        return events.findBySequenceGreaterThanOrderBySequenceAsc(cursor, Limit.of(limit)).stream()
                .map(row ->
                        new TaskEventRecord(row.getSequence(), row.getTaskId(), row.getAction(), row.getOccurredAt()))
                .toList();
    }
}
