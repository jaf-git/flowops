package com.flowops.task.application.viewtasks;

import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskKind;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.model.TaskId;
import java.time.Instant;
import java.util.List;

public record ViewTasksResult(List<Row> tasks) {
    public record Row(
            TaskId id,
            String title,
            java.util.UUID assigneeId,
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
            TaskKind kind,
            java.util.UUID templateId,
            java.util.UUID categoryId,
            String categoryName) {
        public Row(
                TaskId id,
                String title,
                java.util.UUID assigneeId,
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
                TaskKind kind,
                java.util.UUID templateId) {
            this(
                    id,
                    title,
                    assigneeId,
                    assigneeName,
                    deadline,
                    priority,
                    state,
                    openPhase,
                    phaseSince,
                    mine,
                    directedByMe,
                    deadlineProposalOpen,
                    atRisk,
                    false,
                    kind,
                    templateId,
                    null,
                    null);
        }
    }
}
