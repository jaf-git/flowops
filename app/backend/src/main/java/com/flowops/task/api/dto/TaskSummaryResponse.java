package com.flowops.task.api.dto;

import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import java.time.Instant;
import java.util.UUID;

public record TaskSummaryResponse(
        UUID id,
        String title,
        UUID assigneeId,
        String assigneeName,
        Instant deadline,
        TaskPriority priority,
        TaskState state,
        PhaseKind openPhase,
        Instant phaseSince,
        boolean mine,
        boolean directedByMe,
        boolean deadlineProposalOpen,
        boolean atRisk,
        boolean overdue,
        String kind,
        UUID templateId,
        UUID categoryId,
        String categoryName) {}
