package com.flowops.task.infrastructure.persistence;

import com.flowops.task.application.shared.port.LoadCompletionProofPort;
import com.flowops.task.application.shared.port.SaveCompletionProofPort;
import com.flowops.task.domain.model.CompletionProof;
import com.flowops.task.domain.model.CompletionProofId;
import com.flowops.task.domain.model.TaskId;
import com.flowops.task.infrastructure.persistence.entity.TaskCompletionProofJpaEntity;
import com.flowops.task.infrastructure.persistence.repository.TaskCompletionProofJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CompletionProofPersistenceAdapter implements SaveCompletionProofPort, LoadCompletionProofPort {
    private final TaskCompletionProofJpaRepository proofs;

    public CompletionProofPersistenceAdapter(TaskCompletionProofJpaRepository proofs) {
        this.proofs = proofs;
    }

    @Override
    public void save(CompletionProof proof) {
        UUID id = proofs.findByTaskId(proof.task().value())
                .map(TaskCompletionProofJpaEntity::getId)
                .orElseGet(() -> proof.id().value());
        proofs.save(new TaskCompletionProofJpaEntity(
                id, proof.task().value(), proof.note(), proof.link().orElse(null), proof.submittedAt()));
    }

    @Override
    public Optional<CompletionProof> findByTask(TaskId task) {
        return proofs.findByTaskId(task.value())
                .map(entity -> new CompletionProof(
                        CompletionProofId.of(entity.getId()),
                        TaskId.of(entity.getTaskId()),
                        entity.getNote(),
                        entity.getExternalLink(),
                        entity.getSubmittedAt()));
    }
}
