package com.flowops.task.api.mapper;

import com.flowops.task.api.dto.ChecklistItemResponse;
import com.flowops.task.api.dto.DeadlineNoticesResponse;
import com.flowops.task.api.dto.PagedTasksResponse;
import com.flowops.task.api.dto.TaskActivityResponse;
import com.flowops.task.api.dto.TaskCommentResponse;
import com.flowops.task.api.dto.TaskDetailResponse;
import com.flowops.task.api.dto.TaskLinkResponse;
import com.flowops.task.api.dto.TaskMaterialResponse;
import com.flowops.task.api.dto.TaskResponse;
import com.flowops.task.api.dto.TaskSectionsResponse;
import com.flowops.task.api.dto.TaskSummaryResponse;
import com.flowops.task.api.dto.TasksResponse;
import com.flowops.task.application.accepttask.AcceptTaskResult;
import com.flowops.task.application.createtask.CreateTaskResult;
import com.flowops.task.application.shared.TaskTransitionResult;
import com.flowops.task.application.tasklink.ViewTaskMaterialUseCase;
import com.flowops.task.application.viewdeadlinenotices.ViewDeadlineNoticesResult;
import com.flowops.task.application.viewtaskactivity.ActivityEntry;
import com.flowops.task.application.viewtaskdetail.ViewTaskDetailResult;
import com.flowops.task.application.viewtasks.SectionedTasksResult;
import com.flowops.task.application.viewtasks.ViewTasksResult;
import com.flowops.task.domain.model.ChecklistItem;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskComment;
import com.flowops.task.domain.model.TaskLink;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TaskDtoMapper {
    public TaskResponse toResponse(TaskTransitionResult result) {
        return toResponse(result.task(), result.assigneeName(), result.atRisk());
    }

    public TaskResponse toResponse(CreateTaskResult result) {
        return toResponse(result.task(), result.assigneeName(), result.atRisk());
    }

    public TaskResponse toResponse(AcceptTaskResult result) {
        return toResponse(result.task(), result.assigneeName(), result.atRisk());
    }

    public TaskResponse toResponse(Task task, String assigneeName, boolean atRisk) {
        return new TaskResponse(
                task.id().value(),
                task.title(),
                task.description().orElse(null),
                task.assignee().map(PersonId::value).orElse(null),
                assigneeName,
                task.creator().value(),
                task.deadline(),
                task.priority(),
                task.state(),
                task.isSelfAssigned(),
                atRisk,
                task.createdAt());
    }

    public TaskDetailResponse toDetailResponse(ViewTaskDetailResult result) {
        Task task = result.task();
        return new TaskDetailResponse(
                task.id().value(),
                task.title(),
                task.description().orElse(null),
                task.assignee().map(PersonId::value).orElse(null),
                result.assigneeName(),
                task.creator().value(),
                task.deadline(),
                task.priority(),
                task.state(),
                task.isSelfAssigned(),
                task.createdAt(),
                result.openPhase(),
                result.phaseSince(),
                result.phases().stream()
                        .map(span -> new TaskDetailResponse.PhaseSpanResponse(span.kind(), span.seconds()))
                        .toList(),
                result.proof() == null
                        ? null
                        : new TaskDetailResponse.CompletionProofResponse(
                                result.proof().note(),
                                result.proof().link().orElse(null),
                                result.proof().submittedAt()),
                result.approval() == null
                        ? null
                        : new TaskDetailResponse.ApprovalResponse(
                                result.approval().score().value(),
                                result.approval().comment(),
                                result.approval().reviewer().value(),
                                result.approval().decidedAt()),
                result.completedAt(),
                result.deadlineMet().orElse(null),
                result.atRisk(),
                result.overdue(),
                result.openProposal() == null
                        ? null
                        : new TaskDetailResponse.DeadlineProposalResponse(
                                result.openProposal().proposedDeadline(),
                                result.openProposal().reason(),
                                result.openProposal().proposer().value(),
                                result.openProposal().proposedAt()),
                task.provenance().template().orElse(null));
    }

    public DeadlineNoticesResponse toResponse(ViewDeadlineNoticesResult result) {
        return new DeadlineNoticesResponse(result.notices().stream()
                .map(notice -> new DeadlineNoticesResponse.DeadlineNotice(
                        notice.task().value(),
                        notice.title(),
                        notice.assignee().value(),
                        notice.assigneeName(),
                        notice.deadline(),
                        notice.setAt()))
                .toList());
    }

    public TaskMaterialResponse toResponse(ViewTaskMaterialUseCase.Material material) {
        return new TaskMaterialResponse(
                material.links().stream().map(this::toResponse).toList(),
                material.checklist().stream().map(TaskDtoMapper::toResponse).toList());
    }

    public static ChecklistItemResponse toResponse(ChecklistItem item) {
        return new ChecklistItemResponse(
                item.id().value(),
                item.position(),
                item.text(),
                item.isDone(),
                item.doneAt().orElse(null),
                item.authoredBy().value());
    }

    public TaskLinkResponse toResponse(TaskLink link) {
        return new TaskLinkResponse(
                link.id().value(),
                link.url().value(),
                link.label().orElse(null),
                link.displayText(),
                link.role(),
                link.addedBy().value(),
                link.addedAt());
    }

    public TasksResponse toResponse(ViewTasksResult result) {
        return new TasksResponse(result.tasks().stream()
                .map(row -> new TaskSummaryResponse(
                        row.id().value(),
                        row.title(),
                        row.assigneeId(),
                        row.assigneeName(),
                        row.deadline(),
                        row.priority(),
                        row.state(),
                        row.openPhase(),
                        row.phaseSince(),
                        row.mine(),
                        row.directedByMe(),
                        row.deadlineProposalOpen(),
                        row.atRisk(),
                        row.overdue(),
                        row.kind().name(),
                        row.templateId(),
                        row.categoryId(),
                        row.categoryName()))
                .toList());
    }

    public TaskSectionsResponse toResponse(SectionedTasksResult.Counts counts) {
        return new TaskSectionsResponse(
                counts.sections().stream()
                        .map(entry -> new TaskSectionsResponse.SectionCountResponse(
                                entry.section().key(), entry.count()))
                        .toList(),
                counts.total());
    }

    public PagedTasksResponse toResponse(SectionedTasksResult.Page page) {
        return new PagedTasksResponse(
                page.section().key(),
                toResponse(new ViewTasksResult(page.rows())).tasks(),
                page.page(),
                page.size(),
                page.total(),
                page.totalPages());
    }

    public TaskCommentResponse toResponse(TaskComment comment) {
        return new TaskCommentResponse(
                comment.id().value(), comment.author().value(), "", comment.body(), comment.writtenAt());
    }

    public TaskActivityResponse toResponse(List<ActivityEntry> activity) {
        return new TaskActivityResponse(activity.stream()
                .map(entry -> new TaskActivityResponse.Entry(
                        entry.kind(),
                        entry.occurredAt(),
                        entry.actor().value(),
                        entry.actorName(),
                        entry.from(),
                        entry.to(),
                        entry.reason(),
                        entry.overridden(),
                        entry.body()))
                .toList());
    }
}
