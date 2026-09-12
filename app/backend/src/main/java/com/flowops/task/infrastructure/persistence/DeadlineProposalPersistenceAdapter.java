package com.flowops.task.infrastructure.persistence;

import com.flowops.task.application.shared.port.LoadDeadlineProposalPort;
import com.flowops.task.application.shared.port.SaveDeadlineProposalPort;
import com.flowops.task.domain.model.DeadlineProposal;
import com.flowops.task.domain.model.DeadlineProposalId;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;
import com.flowops.task.infrastructure.persistence.entity.TaskDeadlineProposalJpaEntity;
import com.flowops.task.infrastructure.persistence.repository.TaskDeadlineProposalJpaRepository;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DeadlineProposalPersistenceAdapter implements SaveDeadlineProposalPort, LoadDeadlineProposalPort {
    private final TaskDeadlineProposalJpaRepository proposals;

    public DeadlineProposalPersistenceAdapter(TaskDeadlineProposalJpaRepository proposals) {
        this.proposals = proposals;
    }

    @Override
    public void save(DeadlineProposal proposal) {
        proposals.save(toEntity(proposal));
    }

    @Override
    public void update(DeadlineProposal proposal) {
        proposals.save(toEntity(proposal));
    }

    @Override
    public Optional<DeadlineProposal> lockOpenProposalOf(TaskId task) {
        return proposals.lockOpenByTaskId(task.value()).map(DeadlineProposalPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<DeadlineProposal> openProposalOf(TaskId task) {
        return proposals.findByTaskIdAndDecisionIsNull(task.value()).map(DeadlineProposalPersistenceAdapter::toDomain);
    }

    @Override
    public Set<TaskId> withOpenProposalsAmong(Collection<TaskId> tasks) {
        if (tasks.isEmpty()) {
            return Set.of();
        }
        return proposals.openTaskIdsAmong(tasks.stream().map(TaskId::value).toList()).stream()
                .map(TaskId::of)
                .collect(Collectors.toSet());
    }

    private static TaskDeadlineProposalJpaEntity toEntity(DeadlineProposal proposal) {
        return new TaskDeadlineProposalJpaEntity(
                proposal.id().value(),
                proposal.task().value(),
                proposal.proposedDeadline(),
                proposal.reason(),
                proposal.proposer().value(),
                proposal.proposedAt(),
                proposal.decision(),
                proposal.decisionReason(),
                proposal.decidedBy() == null ? null : proposal.decidedBy().value(),
                proposal.decidedAt());
    }

    private static DeadlineProposal toDomain(TaskDeadlineProposalJpaEntity entity) {
        return DeadlineProposal.rebuild(
                DeadlineProposalId.of(entity.getId()),
                TaskId.of(entity.getTaskId()),
                entity.getProposedDeadline(),
                entity.getReason(),
                PersonId.of(entity.getProposerUserId()),
                entity.getProposedAt(),
                entity.getDecision(),
                entity.getDecisionReason(),
                entity.getDecidedByUserId() == null ? null : PersonId.of(entity.getDecidedByUserId()),
                entity.getDecidedAt());
    }
}
