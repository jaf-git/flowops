package com.flowops.process.infrastructure.persistence;

import com.flowops.process.application.shared.port.AppendProcessEventPort;
import com.flowops.process.domain.event.ProcessEvent;
import com.flowops.process.infrastructure.persistence.entity.ProcessEventJpaEntity;
import com.flowops.process.infrastructure.persistence.repository.ProcessEventJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class ProcessEventPersistenceAdapter implements AppendProcessEventPort {
    private final ProcessEventJpaRepository events;

    public ProcessEventPersistenceAdapter(ProcessEventJpaRepository events) {
        this.events = events;
    }

    @Override
    public void append(ProcessEvent event) {
        events.save(new ProcessEventJpaEntity(
                event.id(),
                event.template() == null ? null : event.template().value(),
                event.instance() == null ? null : event.instance().value(),
                event.action().name(),
                event.actor().value(),
                event.occurredAt()));
    }
}
