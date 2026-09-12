package com.flowops.task.infrastructure.persistence;

import com.flowops.task.application.shared.port.LoadApprovalPort;
import com.flowops.task.application.shared.port.SaveApprovalPort;
import com.flowops.task.domain.model.Approval;
import com.flowops.task.domain.model.ApprovalId;
import com.flowops.task.domain.model.ApprovalScore;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;
import com.flowops.task.infrastructure.persistence.entity.TaskApprovalJpaEntity;
import com.flowops.task.infrastructure.persistence.repository.TaskApprovalJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ApprovalPersistenceAdapter implements SaveApprovalPort, LoadApprovalPort {
    private final TaskApprovalJpaRepository approvals;

    public ApprovalPersistenceAdapter(TaskApprovalJpaRepository approvals) {
        this.approvals = approvals;
    }

    @Override
    public void save(Approval approval) {
        approvals.save(new TaskApprovalJpaEntity(
                approval.id().value(),
                approval.task().value(),
                (short) approval.score().value(),
                approval.comment(),
                approval.reviewer().value(),
                approval.decidedAt()));
    }

    @Override
    public Optional<Approval> findByTask(TaskId task) {
        return approvals.findByTaskId(task.value()).map(this::toDomain);
    }

    private Approval toDomain(TaskApprovalJpaEntity entity) {
        return new Approval(
                ApprovalId.of(entity.getId()),
                TaskId.of(entity.getTaskId()),
                new ApprovalScore(entity.getScore()),
                entity.getComment(),
                PersonId.of(entity.getReviewerUserId()),
                entity.getDecidedAt());
    }
}
