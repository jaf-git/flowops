package com.flowops.task.api.dto;

import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskDetailResponse(
        UUID id,
        String title,
        String description,
        UUID assigneeId,
        String assigneeName,
        UUID creatorId,
        Instant deadline,
        TaskPriority priority,
        TaskState state,
        boolean selfAssigned,
        Instant createdAt,
        PhaseKind openPhase,
        Instant phaseSince,
        List<PhaseSpanResponse> phases,
        CompletionProofResponse proof,
        ApprovalResponse approval,
        Instant completedAt,
        Boolean deadlineMet,
        boolean atRisk,
        @Schema(description = "Past its date and still owed. Exclusive with atRisk.") boolean overdue,
        DeadlineProposalResponse deadlineProposal,
        UUID templateId) {
    public record PhaseSpanResponse(PhaseKind kind, long seconds) {}

    public record DeadlineProposalResponse(
            Instant proposedDeadline, String reason, UUID proposerId, Instant proposedAt) {}

    public record CompletionProofResponse(String note, String externalLink, Instant submittedAt) {}

    public record ApprovalResponse(int score, String comment, UUID reviewerId, Instant decidedAt) {}
}
