package com.flowops.task.infrastructure.persistence;

import com.flowops.task.application.shared.port.SaveAmendmentPort;
import com.flowops.task.domain.model.TaskAmendment;
import com.flowops.task.infrastructure.persistence.entity.TaskAmendmentJpaEntity;
import com.flowops.task.infrastructure.persistence.repository.TaskAmendmentJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class AmendmentPersistenceAdapter implements SaveAmendmentPort {
    private final TaskAmendmentJpaRepository amendments;

    public AmendmentPersistenceAdapter(TaskAmendmentJpaRepository amendments) {
        this.amendments = amendments;
    }

    @Override
    public void save(TaskAmendment amendment) {
        amendments.save(new TaskAmendmentJpaEntity(
                amendment.id().value(),
                amendment.task().value(),
                amendment.event(),
                amendment.formerDeadline(),
                amendment.newDeadline(),
                amendment.formerPriority(),
                amendment.newPriority(),
                amendment.formerDescription(),
                amendment.newDescription(),
                amendment.actor().value(),
                amendment.occurredAt()));
    }
}
