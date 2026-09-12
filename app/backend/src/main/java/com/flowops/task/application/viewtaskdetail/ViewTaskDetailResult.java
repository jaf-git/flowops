package com.flowops.task.application.viewtaskdetail;

import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.model.Approval;
import com.flowops.task.domain.model.CompletionProof;
import com.flowops.task.domain.model.DeadlineProposal;
import com.flowops.task.domain.model.Task;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record ViewTaskDetailResult(
        Task task,
        String assigneeName,
        PhaseKind openPhase,
        Instant phaseSince,
        List<PhaseSpan> phases,
        CompletionProof proof,
        Approval approval,
        Instant completedAt,
        boolean atRisk,
        boolean overdue,
        DeadlineProposal openProposal) {
    public record PhaseSpan(PhaseKind kind, long seconds) {}

    public Optional<Boolean> deadlineMet() {
        return Optional.ofNullable(completedAt).map(submitted -> !submitted.isAfter(task.deadline()));
    }
}
