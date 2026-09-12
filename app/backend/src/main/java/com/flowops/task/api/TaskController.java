package com.flowops.task.api;

import com.flowops.shared.web.ErrorResponse;
import com.flowops.task.api.dto.ApproveTaskRequest;
import com.flowops.task.api.dto.AssignablePeopleResponse;
import com.flowops.task.api.dto.AttachLinkRequest;
import com.flowops.task.api.dto.BlockTaskRequest;
import com.flowops.task.api.dto.ChecklistItemRequest;
import com.flowops.task.api.dto.ChecklistItemResponse;
import com.flowops.task.api.dto.CommentOnTaskRequest;
import com.flowops.task.api.dto.CompleteTaskRequest;
import com.flowops.task.api.dto.CreateTaskRequest;
import com.flowops.task.api.dto.DeadlineNoticesResponse;
import com.flowops.task.api.dto.DecideDeadlineRequest;
import com.flowops.task.api.dto.EditTaskRequest;
import com.flowops.task.api.dto.OverrideTaskRequest;
import com.flowops.task.api.dto.PagedTasksResponse;
import com.flowops.task.api.dto.ProposeDeadlineRequest;
import com.flowops.task.api.dto.ReassignTaskRequest;
import com.flowops.task.api.dto.RejectTaskRequest;
import com.flowops.task.api.dto.ReturnTaskRequest;
import com.flowops.task.api.dto.SetDeadlineRequest;
import com.flowops.task.api.dto.TaskActivityResponse;
import com.flowops.task.api.dto.TaskCommentResponse;
import com.flowops.task.api.dto.TaskDetailResponse;
import com.flowops.task.api.dto.TaskLinkResponse;
import com.flowops.task.api.dto.TaskMaterialResponse;
import com.flowops.task.api.dto.TaskResponse;
import com.flowops.task.api.dto.TaskSectionsResponse;
import com.flowops.task.api.dto.TasksResponse;
import com.flowops.task.api.dto.TickChecklistItemRequest;
import com.flowops.task.api.dto.UnblockTaskRequest;
import com.flowops.task.api.mapper.TaskDtoMapper;
import com.flowops.task.application.accepttask.AcceptTaskCommand;
import com.flowops.task.application.accepttask.AcceptTaskResult;
import com.flowops.task.application.accepttask.AcceptTaskUseCase;
import com.flowops.task.application.acknowledgedeadlinenotice.AcknowledgeDeadlineNoticeCommand;
import com.flowops.task.application.acknowledgedeadlinenotice.AcknowledgeDeadlineNoticeUseCase;
import com.flowops.task.application.approvetask.ApproveTaskCommand;
import com.flowops.task.application.approvetask.ApproveTaskUseCase;
import com.flowops.task.application.blocktask.BlockTaskCommand;
import com.flowops.task.application.blocktask.BlockTaskUseCase;
import com.flowops.task.application.closetask.CloseTaskCommand;
import com.flowops.task.application.closetask.CloseTaskUseCase;
import com.flowops.task.application.commenttask.CommentOnTaskUseCase;
import com.flowops.task.application.completetask.CompleteTaskCommand;
import com.flowops.task.application.completetask.CompleteTaskUseCase;
import com.flowops.task.application.createtask.CreateTaskCommand;
import com.flowops.task.application.createtask.CreateTaskResult;
import com.flowops.task.application.createtask.CreateTaskUseCase;
import com.flowops.task.application.decidedeadline.DecideDeadlineCommand;
import com.flowops.task.application.decidedeadline.DecideDeadlineUseCase;
import com.flowops.task.application.edittask.EditTaskCommand;
import com.flowops.task.application.edittask.EditTaskUseCase;
import com.flowops.task.application.overridetask.OverrideTaskUseCase;
import com.flowops.task.application.proposedeadline.ProposeDeadlineCommand;
import com.flowops.task.application.proposedeadline.ProposeDeadlineUseCase;
import com.flowops.task.application.reassigntask.ReassignTaskUseCase;
import com.flowops.task.application.rejecttask.RejectTaskCommand;
import com.flowops.task.application.rejecttask.RejectTaskUseCase;
import com.flowops.task.application.returntaskforrework.ReturnTaskForReworkCommand;
import com.flowops.task.application.returntaskforrework.ReturnTaskForReworkUseCase;
import com.flowops.task.application.settaskdeadline.SetTaskDeadlineCommand;
import com.flowops.task.application.settaskdeadline.SetTaskDeadlineUseCase;
import com.flowops.task.application.shared.TaskTransitionResult;
import com.flowops.task.application.starttask.StartTaskCommand;
import com.flowops.task.application.starttask.StartTaskUseCase;
import com.flowops.task.application.taskchecklist.AddChecklistItemCommand;
import com.flowops.task.application.taskchecklist.RemoveChecklistItemCommand;
import com.flowops.task.application.taskchecklist.TaskChecklistUseCase;
import com.flowops.task.application.taskchecklist.TickChecklistItemCommand;
import com.flowops.task.application.tasklink.AttachLinkCommand;
import com.flowops.task.application.tasklink.DetachLinkCommand;
import com.flowops.task.application.tasklink.TaskLinkUseCase;
import com.flowops.task.application.tasklink.ViewTaskMaterialUseCase;
import com.flowops.task.application.unblocktask.UnblockTaskCommand;
import com.flowops.task.application.unblocktask.UnblockTaskUseCase;
import com.flowops.task.application.viewassignablepeople.ViewAssignablePeopleUseCase;
import com.flowops.task.application.viewdeadlinenotices.ViewDeadlineNoticesUseCase;
import com.flowops.task.application.viewreviewqueue.ViewReviewQueueUseCase;
import com.flowops.task.application.viewtaskactivity.ViewTaskActivityUseCase;
import com.flowops.task.application.viewtaskdetail.ViewTaskDetailUseCase;
import com.flowops.task.application.viewtasks.SectionedTaskQueryUseCase;
import com.flowops.task.application.viewtasks.TaskFilter;
import com.flowops.task.application.viewtasks.TaskSection;
import com.flowops.task.application.viewtasks.TaskSort;
import com.flowops.task.application.viewtasks.ViewTasksUseCase;
import com.flowops.task.application.viewthroughput.ViewThroughputUseCase;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.model.ChecklistItemId;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskComment;
import com.flowops.task.domain.model.TaskId;
import com.flowops.task.domain.model.TaskLinkId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks", description = "A piece of work, from the moment it is given to the moment it is closed.")
public class TaskController {
    private final CreateTaskUseCase createTaskUseCase;
    private final AcceptTaskUseCase acceptTaskUseCase;
    private final StartTaskUseCase startTaskUseCase;
    private final BlockTaskUseCase blockTaskUseCase;
    private final UnblockTaskUseCase unblockTaskUseCase;
    private final CompleteTaskUseCase completeTaskUseCase;
    private final ViewTasksUseCase viewTasksUseCase;
    private final SectionedTaskQueryUseCase sectionedTaskQueryUseCase;
    private final ViewAssignablePeopleUseCase viewAssignablePeopleUseCase;
    private final ApproveTaskUseCase approveTaskUseCase;
    private final ReturnTaskForReworkUseCase returnTaskForReworkUseCase;
    private final CloseTaskUseCase closeTaskUseCase;
    private final ViewReviewQueueUseCase viewReviewQueueUseCase;
    private final ViewTaskDetailUseCase viewTaskDetailUseCase;
    private final ViewThroughputUseCase viewThroughputUseCase;
    private final RejectTaskUseCase rejectTaskUseCase;
    private final ProposeDeadlineUseCase proposeDeadlineUseCase;
    private final DecideDeadlineUseCase decideDeadlineUseCase;
    private final EditTaskUseCase editTaskUseCase;
    private final SetTaskDeadlineUseCase setTaskDeadlineUseCase;
    private final TaskLinkUseCase taskLinkUseCase;
    private final TaskChecklistUseCase taskChecklistUseCase;
    private final ViewTaskMaterialUseCase viewTaskMaterialUseCase;
    private final ViewDeadlineNoticesUseCase viewDeadlineNoticesUseCase;
    private final AcknowledgeDeadlineNoticeUseCase acknowledgeDeadlineNoticeUseCase;
    private final CommentOnTaskUseCase commentOnTaskUseCase;
    private final ViewTaskActivityUseCase viewTaskActivityUseCase;
    private final ReassignTaskUseCase reassignTaskUseCase;
    private final OverrideTaskUseCase overrideTaskUseCase;
    private final TaskDtoMapper mapper;

    public TaskController(
            SetTaskDeadlineUseCase setTaskDeadlineUseCase,
            TaskLinkUseCase taskLinkUseCase,
            TaskChecklistUseCase taskChecklistUseCase,
            ViewTaskMaterialUseCase viewTaskMaterialUseCase,
            ViewDeadlineNoticesUseCase viewDeadlineNoticesUseCase,
            AcknowledgeDeadlineNoticeUseCase acknowledgeDeadlineNoticeUseCase,
            CreateTaskUseCase createTaskUseCase,
            AcceptTaskUseCase acceptTaskUseCase,
            StartTaskUseCase startTaskUseCase,
            BlockTaskUseCase blockTaskUseCase,
            UnblockTaskUseCase unblockTaskUseCase,
            CompleteTaskUseCase completeTaskUseCase,
            ViewTasksUseCase viewTasksUseCase,
            SectionedTaskQueryUseCase sectionedTaskQueryUseCase,
            ViewAssignablePeopleUseCase viewAssignablePeopleUseCase,
            ApproveTaskUseCase approveTaskUseCase,
            ReturnTaskForReworkUseCase returnTaskForReworkUseCase,
            CloseTaskUseCase closeTaskUseCase,
            ViewReviewQueueUseCase viewReviewQueueUseCase,
            ViewTaskDetailUseCase viewTaskDetailUseCase,
            ViewThroughputUseCase viewThroughputUseCase,
            RejectTaskUseCase rejectTaskUseCase,
            ProposeDeadlineUseCase proposeDeadlineUseCase,
            DecideDeadlineUseCase decideDeadlineUseCase,
            EditTaskUseCase editTaskUseCase,
            CommentOnTaskUseCase commentOnTaskUseCase,
            ViewTaskActivityUseCase viewTaskActivityUseCase,
            ReassignTaskUseCase reassignTaskUseCase,
            OverrideTaskUseCase overrideTaskUseCase,
            TaskDtoMapper mapper) {
        this.setTaskDeadlineUseCase = setTaskDeadlineUseCase;
        this.taskLinkUseCase = taskLinkUseCase;
        this.taskChecklistUseCase = taskChecklistUseCase;
        this.viewTaskMaterialUseCase = viewTaskMaterialUseCase;
        this.viewDeadlineNoticesUseCase = viewDeadlineNoticesUseCase;
        this.acknowledgeDeadlineNoticeUseCase = acknowledgeDeadlineNoticeUseCase;
        this.createTaskUseCase = createTaskUseCase;
        this.acceptTaskUseCase = acceptTaskUseCase;
        this.startTaskUseCase = startTaskUseCase;
        this.blockTaskUseCase = blockTaskUseCase;
        this.unblockTaskUseCase = unblockTaskUseCase;
        this.completeTaskUseCase = completeTaskUseCase;
        this.viewTasksUseCase = viewTasksUseCase;
        this.sectionedTaskQueryUseCase = sectionedTaskQueryUseCase;
        this.viewAssignablePeopleUseCase = viewAssignablePeopleUseCase;
        this.approveTaskUseCase = approveTaskUseCase;
        this.returnTaskForReworkUseCase = returnTaskForReworkUseCase;
        this.closeTaskUseCase = closeTaskUseCase;
        this.viewReviewQueueUseCase = viewReviewQueueUseCase;
        this.viewTaskDetailUseCase = viewTaskDetailUseCase;
        this.viewThroughputUseCase = viewThroughputUseCase;
        this.rejectTaskUseCase = rejectTaskUseCase;
        this.proposeDeadlineUseCase = proposeDeadlineUseCase;
        this.decideDeadlineUseCase = decideDeadlineUseCase;
        this.editTaskUseCase = editTaskUseCase;
        this.commentOnTaskUseCase = commentOnTaskUseCase;
        this.viewTaskActivityUseCase = viewTaskActivityUseCase;
        this.reassignTaskUseCase = reassignTaskUseCase;
        this.overrideTaskUseCase = overrideTaskUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TASK_CREATE')")
    @Operation(
            summary = "Give a piece of work to somebody",
            description = "TASK-CREATE-01. Requires TASK_CREATE at this endpoint, and the assignee must be an "
                    + "active person inside the caller's part of the reporting tree, which is checked in the "
                    + "use case because it depends on the request. The task always enters Created and its wait "
                    + "phase opens with it.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The task, as created"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID when the title is blank or the deadline is absent; MALFORMED_BODY "
                        + "when the body could not be read",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_CREATE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "DEADLINE_IN_THE_PAST, ASSIGNEE_NOT_ACTIVE or ASSIGNEE_OUT_OF_SCOPE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        CreateTaskResult result = createTaskUseCase.execute(new CreateTaskCommand(
                request.title(),
                request.description(),
                request.assigneeId(),
                request.deadline(),
                (request.priority() == null ? TaskPriority.NORMAL : request.priority()).name(),
                request.templateId(),
                null,
                request.kind()));

        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(result));
    }

    @GetMapping("/assignable-people")
    @PreAuthorize("hasAuthority('TASK_CREATE')")
    @Operation(
            summary = "Who I may give work to",
            description = "The picker behind TASK-CREATE-01. Requires TASK_CREATE, because it answers a "
                    + "question only somebody who may create work has any use for. It returns the active "
                    + "people inside the caller's own part of the reporting tree plus the caller themselves, "
                    + "computed from the same ports the create rule enforces — a screen filtered by one rule "
                    + "and a server enforcing another is how somebody comes to be offered a refusal. "
                    + "Self-assignment is permitted, which is why the caller is in their own list.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The people this caller may assign to"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_CREATE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AssignablePeopleResponse> assignablePeople() {
        return ResponseEntity.ok(new AssignablePeopleResponse(viewAssignablePeopleUseCase.execute().stream()
                .map(person -> new AssignablePeopleResponse.AssignablePerson(person.id(), person.displayName()))
                .toList()));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('TASK_ACT_OWN')")
    @Operation(
            summary = "Acknowledge that a task is mine",
            description = "TASK-ACCEPT-01. Requires TASK_ACT_OWN at this endpoint, which every employee holds "
                    + "for their own work, so the identity check in the use case is the whole of the "
                    + "protection: a manager may not accept on somebody's behalf. Accepting after the deadline "
                    + "succeeds, because refusing would leave an overdue task permanently stuck.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The task, now Accepted"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED, or NOT_THE_ASSIGNEE when the work is somebody else's",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "ILLEGAL_TRANSITION, naming the state the task is actually in",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskResponse> accept(@PathVariable UUID id) {
        AcceptTaskResult result = acceptTaskUseCase.execute(new AcceptTaskCommand(TaskId.of(id)));
        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('TASK_ACT_OWN')")
    @Operation(
            summary = "Begin work on a task of mine",
            description = "TASK-START-01. Requires TASK_ACT_OWN at this endpoint and identity equality with the "
                    + "assignee in the use case, which is the whole of the protection because every role holds "
                    + "that permission for its own work. This is the transition that opens the active phase — the "
                    + "only interval in the product attributed to a person. Starting from Created is refused: "
                    + "accepting is a separate acknowledgement and skipping it would erase the difference between "
                    + "unseen and unstarted.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The task, now InProgress"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED, or NOT_THE_ASSIGNEE when the work is somebody else's",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "ILLEGAL_TRANSITION, naming the state the task is actually in",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskResponse> start(@PathVariable UUID id) {
        TaskTransitionResult result = startTaskUseCase.execute(new StartTaskCommand(TaskId.of(id)));
        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @PostMapping("/{id}/block")
    @PreAuthorize("hasAuthority('TASK_ACT_OWN')")
    @Operation(
            summary = "Say that I cannot proceed, and why",
            description = "TASK-BLOCK-01. Requires TASK_ACT_OWN and identity equality with the assignee: a "
                    + "manager who believes work is stuck raises it with the person rather than asserting a block "
                    + "on their behalf. The reason is required — an unexplained block is unactionable — and is "
                    + "refused both by bean validation here and by the domain behind it, because PROCESS will "
                    + "reach the same use case with no form in front of it. Blocked time is excluded from what is "
                    + "attributed to the assignee; the deadline still applies.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The task, now Blocked"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID when no reason was given",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED, or NOT_THE_ASSIGNEE when the work is somebody else's",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "ILLEGAL_TRANSITION, naming the state the task is actually in",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskResponse> block(@PathVariable UUID id, @Valid @RequestBody BlockTaskRequest request) {
        TaskTransitionResult result = blockTaskUseCase.execute(new BlockTaskCommand(TaskId.of(id), request.reason()));
        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @PostMapping("/{id}/unblock")
    @PreAuthorize("hasAuthority('TASK_ACT_OWN')")
    @Operation(
            summary = "Say that I can proceed again",
            description = "TASK-UNBLOCK-01. Requires TASK_ACT_OWN and identity equality with the assignee: "
                    + "unblocking asserts that somebody can work again, which only they know. The blocked phase "
                    + "closes and a new active phase opens — a new row, never the earlier one resumed, because "
                    + "total active time is the sum of the rows. The resolution note is optional, unlike the "
                    + "block's reason: requiring an explanation to stop being stuck would be a toll on good news.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The task, back in InProgress"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED, or NOT_THE_ASSIGNEE when the work is somebody else's",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "ILLEGAL_TRANSITION, naming the state the task is actually in",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskResponse> unblock(
            @PathVariable UUID id, @Valid @RequestBody(required = false) UnblockTaskRequest request) {
        TaskTransitionResult result = unblockTaskUseCase.execute(
                new UnblockTaskCommand(TaskId.of(id), request == null ? null : request.resolution()));
        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('TASK_ACT_OWN')")
    @Operation(
            summary = "Submit finished work with evidence of what I did",
            description = "TASK-COMPLETE-01. Requires TASK_ACT_OWN and identity equality with the assignee. The "
                    + "active phase closes and the review phase opens, so review latency is never added to how "
                    + "long the assignee took. The proof note is required — completion without evidence is an "
                    + "assertion and the review that follows would have nothing to review. The external link is "
                    + "optional and is stored inert: the system never fetches it. Completing after the deadline "
                    + "succeeds and is recorded as late, because refusing would leave overdue work permanently "
                    + "unfinishable.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The task, now Completed and awaiting review"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID when no proof note was given",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED, or NOT_THE_ASSIGNEE when the work is somebody else's",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "ILLEGAL_TRANSITION, naming the state the task is actually in",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskResponse> complete(
            @PathVariable UUID id, @Valid @RequestBody CompleteTaskRequest request) {
        TaskTransitionResult result = completeTaskUseCase.execute(
                new CompleteTaskCommand(TaskId.of(id), request.note(), request.externalLink()));
        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TASK_VIEW_OWN')")
    @Operation(
            summary = "The work I can see",
            description = "Requires TASK_VIEW_OWN at this endpoint. How much comes back is decided in the use "
                    + "case by what else the caller holds: their own work, plus their reporting subtree with "
                    + "TASK_VIEW_SUBTREE, plus everything with TASK_VIEW_ANY. An annotation can refuse a "
                    + "caller and cannot widen a response, which is why the other two are read there. The "
                    + "screen that consumes this belongs to MYWORK, which is not yet specified; the endpoint "
                    + "is TASK's because TASK owns the task and therefore owns who may read one.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The tasks this caller may see, newest first"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_VIEW_OWN",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TasksResponse> list() {
        return ResponseEntity.ok(mapper.toResponse(viewTasksUseCase.execute()));
    }

    @GetMapping("/throughput")
    @PreAuthorize("hasAuthority('TASK_VIEW_OWN')")
    @Operation(
            summary = "How much work arrived and how much finished, week by week",
            description = "Requires TASK_VIEW_OWN at this endpoint; how much is counted is decided in the use "
                    + "case by what else the caller holds, exactly as the queue decides it. This endpoint "
                    + "implements no scope of its own. "
                    + "**Two series, because one answers nothing.** *Forty closed this week* is good if thirty "
                    + "arrived and bad if sixty did; the pair is what lets a business see whether it is keeping "
                    + "up. Both are counted from `task_state_transition` in one statement, so they always "
                    + "describe the same population at the same instant. "
                    + "**Weeks with no activity come back with zeroes.** Omitting them would draw two busy "
                    + "fortnights side by side and hide the quiet one between them, which is the shape somebody "
                    + "reading this is looking for. Weeks begin on Monday. "
                    + "`weeks` is clamped to 4..52. A year is where weekly bars stop being readable, and beyond "
                    + "it the honest answer is a different chart at a different grain. "
                    + "Nothing here is per person. There is no parameter that could narrow it to somebody "
                    + "(DECISION-WORKLOAD-CAPACITY-01).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "One row per week, oldest first, empty weeks included"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_VIEW_OWN",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<ViewThroughputUseCase.Week>> throughput(@RequestParam(defaultValue = "12") int weeks) {
        return ResponseEntity.ok(viewThroughputUseCase.of(weeks));
    }

    @GetMapping("/sections")
    @PreAuthorize("hasAuthority('TASK_VIEW_OWN')")
    @Operation(
            summary = "How much work is in each band",
            description = "MYWORK-FILTER-QUEUE-01. Requires TASK_VIEW_OWN at this endpoint; how much is counted "
                    + "is decided in the use case by what else the caller holds, exactly as the unsectioned "
                    + "queue decides it — this endpoint narrows and counts rows that read has already scoped, "
                    + "and implements no scope of its own. Every band comes back including the empty ones, in "
                    + "urgency order, with the total beside them so a client never adds the counts up itself. "
                    + "The filter arguments are applied before the counting, so a count always describes the "
                    + "rows a person would see if they opened that band.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Every band, with its count, under the given filter"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_VIEW_OWN",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskSectionsResponse> sections(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID assignee,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID category) {
        return ResponseEntity.ok(mapper.toResponse(
                sectionedTaskQueryUseCase.count(new TaskFilter(q, assignee, priority, from, to, category))));
    }

    @GetMapping(params = "section")
    @PreAuthorize("hasAuthority('TASK_VIEW_OWN')")
    @Operation(
            summary = "One page of one band",
            description = "MYWORK-FILTER-QUEUE-01. Requires TASK_VIEW_OWN at this endpoint and inherits the "
                    + "use case's scope exactly as the unsectioned queue does. An unknown band, a page past "
                    + "the end and a filter matching nothing all answer 200 with no rows rather than an error: "
                    + "each is a normal thing to arrive with from a stale bookmark, and none of them is a "
                    + "fault the person who followed the link could act on.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The requested page of the requested band"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_VIEW_OWN",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PagedTasksResponse> section(
            @RequestParam String section,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID assignee,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID category) {
        TaskSection band = TaskSection.byKey(section);
        if (band == null) {
            return ResponseEntity.ok(new PagedTasksResponse(section, List.of(), page, size, 0, 1));
        }

        int bounded = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(mapper.toResponse(sectionedTaskQueryUseCase.page(
                band,
                new TaskFilter(q, assignee, priority, from, to, category),
                TaskSort.byKeyOrDefault(sort),
                Math.max(page, 0),
                bounded)));
    }

    @GetMapping("/review-queue")
    @PreAuthorize("hasAuthority('TASK_REVIEW')")
    @Operation(
            summary = "The work waiting on my judgement",
            description = "TASK-REVIEW-01. Requires TASK_REVIEW at this endpoint; how much comes back is decided "
                    + "in the use case from the reporting tree as it stands at this moment, so a reporting-line "
                    + "change takes effect on the next request rather than after a cache expires. Completed work "
                    + "only, oldest first — the queue exists so that nothing sits finished and unnoticed, and "
                    + "newest-first would bury the task that has waited longest. Reading appends no event and "
                    + "notifies nobody: a manager checking whether work is moving should not have to announce it. "
                    + "A task assigned to the caller appears here too, because a person may see their own finished "
                    + "work; what they may not do is judge it.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Completed tasks in the caller's scope, oldest first"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_REVIEW",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TasksResponse> reviewQueue() {
        return ResponseEntity.ok(mapper.toResponse(viewReviewQueueUseCase.execute()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TASK_VIEW_OWN')")
    @Operation(
            summary = "One task, in full",
            description = "TASK-REVIEW-01, the second half. Requires TASK_VIEW_OWN at this endpoint and the task "
                    + "being inside the caller's reach in the use case. It returns what was delivered, whether the "
                    + "deadline was met, and the time broken down by phase — active, blocked, wait, review and "
                    + "approval separately, never as a total, because an unqualified duration would silently "
                    + "attribute to the assignee intervals that were never theirs. The interval still open is "
                    + "measured to the moment of this read and named as open. Nothing is written and nobody is "
                    + "notified.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The task, its evidence, its judgement and its phases"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED, or TASK_OUT_OF_SCOPE when the task belongs to somebody outside the "
                        + "caller's subtree",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskDetailResponse> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(mapper.toDetailResponse(viewTaskDetailUseCase.execute(TaskId.of(id))));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('TASK_REVIEW')")
    @Operation(
            summary = "Accept finished work, and record how it was",
            description = "TASK-APPROVE-01. Requires TASK_REVIEW at this endpoint, the task inside the caller's "
                    + "subtree, and — enforced in the domain rather than here — that the reviewer is not the "
                    + "assignee. That last refusal is enabler E-03 and it fires on identity: it never consults the "
                    + "caller's role, their permissions, or whose authority they are acting under, so no "
                    + "combination of delegation produces an exception. The review phase closes and the approval "
                    + "phase opens, so review latency is the reviewer's rather than the assignee's. The score is "
                    + "one to five and describes this piece of work; no query, endpoint or export groups scores by "
                    + "assignee.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The task, now Approved"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID when no score was given or it is outside one to five",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED; CANNOT_REVIEW_OWN_WORK when the task is the caller's own; "
                        + "TASK_OUT_OF_SCOPE when it is outside their subtree",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "ILLEGAL_TRANSITION, naming the state the task is actually in",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskResponse> approve(@PathVariable UUID id, @Valid @RequestBody ApproveTaskRequest request) {
        TaskTransitionResult result =
                approveTaskUseCase.execute(new ApproveTaskCommand(TaskId.of(id), request.score(), request.comment()));
        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAuthority('TASK_REVIEW')")
    @Operation(
            summary = "Send work back to be finished, with a reason",
            description = "TASK-REJECT-IN-REVIEW-01. The same permission, the same scope bound and the same "
                    + "identity refusal as approving: a person cannot judge their own work in either direction. "
                    + "The task returns to InProgress and never to Created, so the assignee keeps their acceptance "
                    + "and the record reads as iteration rather than as a fresh assignment; the deadline is "
                    + "unchanged. A new active phase opens rather than the earlier one resuming, and the previous "
                    + "proof is kept so the reviewer can see what changed. No score is recorded — a score belongs "
                    + "to approval alone.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The task, back in InProgress"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID when no reason was given",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED; CANNOT_REVIEW_OWN_WORK; TASK_OUT_OF_SCOPE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "ILLEGAL_TRANSITION, naming the state the task is actually in",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskResponse> returnForRework(
            @PathVariable UUID id, @Valid @RequestBody ReturnTaskRequest request) {
        TaskTransitionResult result =
                returnTaskForReworkUseCase.execute(new ReturnTaskForReworkCommand(TaskId.of(id), request.reason()));
        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('TASK_CLOSE')")
    @Operation(
            summary = "Close approved work",
            description = "TASK-CLOSE-01. Requires TASK_CLOSE at this endpoint and the task inside the caller's "
                    + "subtree. Only from Approved: closing unapproved work would let the review step be skipped, "
                    + "which is the whole point of having one. The approval phase closes and nothing opens — this "
                    + "is the only transition in the product that leaves no interval accruing. Closing deletes "
                    + "nothing; every phase, transition, proof and approval stays readable, and the task leaves the "
                    + "active view rather than the record. Closed is terminal in this pass: no transition leaves "
                    + "it, and closing an already-closed task is refused with nothing appended, because an event "
                    + "asserting a transition that did not happen would be a false row in an append-only log.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The task, now Closed, with no phase open"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED, or TASK_OUT_OF_SCOPE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "ILLEGAL_TRANSITION, naming the state the task is actually in — including CLOSED "
                        + "when it has already been closed",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskResponse> close(@PathVariable UUID id) {
        TaskTransitionResult result = closeTaskUseCase.execute(new CloseTaskCommand(TaskId.of(id)));
        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('TASK_ACT_OWN')")
    @Operation(
            summary = "Decline work that is not mine to do",
            description = "TASK-REJECT-01. Requires TASK_ACT_OWN and identity equality with the assignee: only "
                    + "the person holding the work can say it is not theirs. The assignee is cleared and the task "
                    + "stays in Created, so it returns to whoever gave it out rather than sitting unstarted on "
                    + "somebody who has already refused it — there is no Rejected state, because a dedicated one "
                    + "would be a dead end needing its own escape. The reason is required: a rejection without one "
                    + "moves the problem to the assigner with nothing to act on. Only from Created; after "
                    + "acceptance the route is TASK-REASSIGN-01, which is the assigner's act.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The task, still Created and now unassigned"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID when no reason was given",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED, or NOT_THE_ASSIGNEE when the work is somebody else's",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "ILLEGAL_TRANSITION, naming the state the task is actually in",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskResponse> reject(@PathVariable UUID id, @Valid @RequestBody RejectTaskRequest request) {
        TaskTransitionResult result = rejectTaskUseCase.execute(new RejectTaskCommand(TaskId.of(id), request.reason()));
        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @Operation(
            summary = "What a task is worked from, what it produced, and the steps through it",
            description = "TASK-LINK-01 and TASK-CHECKLIST-01. Visible to the person doing the work and the person"
                    + " who gave it out — the same rule that decides who may attach. Empty lists are the ordinary"
                    + " answer and the screen renders nothing at all for them.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Both lists; possibly both empty"),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_THE_ASSIGNEE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "TASK_IS_CLOSED -- the claim refuses a closed task before it reads either list",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}/material")
    @PreAuthorize("hasAuthority('TASK_VIEW_OWN')")
    public ResponseEntity<TaskMaterialResponse> material(@PathVariable UUID id) {
        return ResponseEntity.ok(mapper.toResponse(viewTaskMaterialUseCase.execute(TaskId.of(id))));
    }

    @Operation(
            summary = "Attach something the work is done from or produced (TASK-LINK-01)",
            description = "Only http and https are stored. A javascript: address rendered as an anchor is stored"
                    + " cross-site scripting, and this is the first place in the product where free text one person"
                    + " wrote becomes an element another person clicks — so the rule is an allow-list, in the"
                    + " domain, where every caller passes rather than only the ones arriving from a form.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Attached"),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_THE_ASSIGNEE — neither doing the work nor having given it out",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "TASK_IS_CLOSED — a closed task is a record",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID -- an empty or blank address",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "LINK_SCHEME_NOT_ALLOWED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/links")
    @PreAuthorize("hasAuthority('TASK_ACT_OWN')")
    public ResponseEntity<TaskLinkResponse> attachLink(
            @PathVariable UUID id, @Valid @RequestBody AttachLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toResponse(taskLinkUseCase.attach(
                        new AttachLinkCommand(TaskId.of(id), request.url(), request.label(), request.role()))));
    }

    @Operation(summary = "Take a link off the task (TASK-LINK-01)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Detached"),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_THE_ASSIGNEE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND, including a link identifier belonging to another task",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "TASK_IS_CLOSED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}/links/{linkId}")
    @PreAuthorize("hasAuthority('TASK_ACT_OWN')")
    public ResponseEntity<Void> detachLink(@PathVariable UUID id, @PathVariable UUID linkId) {
        taskLinkUseCase.detach(new DetachLinkCommand(TaskId.of(id), TaskLinkId.of(linkId)));
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Write a step onto the checklist (TASK-CHECKLIST-01)",
            description = "Open to the person doing the work and the person who gave it out. A step has no state, no"
                    + " assignee and no deadline: a part with any of those is a sub-task, and a thing whose parts"
                    + " are work is a Process (DECISION-TASK-PARTS-01).")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Added at the end of the list"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID -- a step with nothing written on it",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_THE_ASSIGNEE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "TASK_IS_CLOSED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/checklist")
    @PreAuthorize("hasAuthority('TASK_ACT_OWN')")
    public ResponseEntity<ChecklistItemResponse> addChecklistItem(
            @PathVariable UUID id, @Valid @RequestBody ChecklistItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TaskDtoMapper.toResponse(
                        taskChecklistUseCase.add(new AddChecklistItemCommand(TaskId.of(id), request.text()))));
    }

    @Operation(
            summary = "Tick a step, or untick it (TASK-CHECKLIST-01)",
            description = "The assignee alone. A tick asserts that a piece of work was done, and asserting that for"
                    + " another person makes the record false — the argument TASK-ACCEPT-01 makes about acceptance,"
                    + " met a third time. It blocks nothing: a task completes with every step unticked.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The step, ticked or unticked"),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_THE_ASSIGNEE — including the person who gave the work out",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND, including a step belonging to another task",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "TASK_IS_CLOSED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/checklist/{itemId}")
    @PreAuthorize("hasAuthority('TASK_ACT_OWN')")
    public ResponseEntity<ChecklistItemResponse> tickChecklistItem(
            @PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody TickChecklistItemRequest request) {
        return ResponseEntity.ok(TaskDtoMapper.toResponse(taskChecklistUseCase.tick(
                new TickChecklistItemCommand(TaskId.of(id), ChecklistItemId.of(itemId), request.done()))));
    }

    @Operation(summary = "Remove a step that turned out not to be one (TASK-CHECKLIST-01)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Removed"),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_THE_ASSIGNEE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "TASK_IS_CLOSED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}/checklist/{itemId}")
    @PreAuthorize("hasAuthority('TASK_ACT_OWN')")
    public ResponseEntity<Void> removeChecklistItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        taskChecklistUseCase.remove(new RemoveChecklistItemCommand(TaskId.of(id), ChecklistItemId.of(itemId)));
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Set the deadline on work I have accepted (TASK-SET-DEADLINE-01)",
            description = "The assignee's own date. Permitted only from Accepted: before work begins the date is"
                    + " theirs and may be re-set freely; once it has begun the route is a proposal, which the"
                    + " assigner decides. Requires TASK_ACT_OWN and identity equality with the assignee.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The date is set; the task has not moved"),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_THE_ASSIGNEE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "ILLEGAL_TRANSITION before acceptance, USE_A_PROPOSAL_INSTEAD once work has begun,"
                        + " NOTHING_CHANGED for the date it already carries",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "DEADLINE_IN_THE_PAST",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/deadline")
    @PreAuthorize("hasAuthority('TASK_ACT_OWN')")
    public ResponseEntity<TaskResponse> setDeadline(
            @PathVariable UUID id, @Valid @RequestBody SetDeadlineRequest request) {
        return ResponseEntity.ok(mapper.toResponse(
                setTaskDeadlineUseCase.execute(new SetTaskDeadlineCommand(TaskId.of(id), request.deadline()))));
    }

    @Operation(
            summary = "Dates my assignees set that I have not acknowledged (TASK-SET-DEADLINE-01)",
            description = "Structurally self-scoped: the subject is the authenticated caller and there is no"
                    + " parameter for a person, so there is no authorization decision to get wrong. Empty is the"
                    + " ordinary answer and is not an error.")
    @ApiResponse(responseCode = "200", description = "The notices, newest first; possibly none")
    @GetMapping("/deadline-notices")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeadlineNoticesResponse> deadlineNotices() {
        return ResponseEntity.ok(mapper.toResponse(viewDeadlineNoticesUseCase.execute()));
    }

    @Operation(
            summary = "Acknowledge a date my assignee set (TASK-SET-DEADLINE-01)",
            description = "Dismisses the notice. Changes nothing about the work and tells nobody: acknowledging is"
                    + " not a decision, and announcing it would make it read as one. Acknowledging when there is"
                    + " nothing outstanding succeeds, so a double press is not an error.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Acknowledged"),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_THE_CREATOR",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/deadline-notice/acknowledge")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> acknowledgeDeadlineNotice(@PathVariable UUID id) {
        acknowledgeDeadlineNoticeUseCase.execute(new AcknowledgeDeadlineNoticeCommand(TaskId.of(id)));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deadline-proposals")
    @PreAuthorize("hasAuthority('TASK_ACT_OWN')")
    @Operation(
            summary = "Ask for a different deadline",
            description = "TASK-PROPOSE-DEADLINE-01. Requires TASK_ACT_OWN and identity equality with the "
                    + "assignee. It says *yes, but not by then*, which is a different act from declining the work "
                    + "— collapsing the two would force somebody to refuse work they are willing to do. "
                    + "**The task does not move**: it stays in Created and the wait phase keeps running, because "
                    + "pausing it would let a proposal be used to stop the clock. One proposal is open at a time, "
                    + "so that who is waiting on whom has exactly one answer, and a second is refused naming the "
                    + "one already open. A date earlier than the current deadline is permitted: offering to "
                    + "finish sooner is a change like any other. Nothing about the deadline changes until the "
                    + "assigner decides.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The task, unchanged, with the proposal recorded"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID when the date or the reason is missing",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED, or NOT_THE_ASSIGNEE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "PROPOSAL_ALREADY_OPEN, naming the date already asked for; or ILLEGAL_TRANSITION "
                        + "when the task has left Created",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "DEADLINE_IN_THE_PAST",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskResponse> proposeDeadline(
            @PathVariable UUID id, @Valid @RequestBody ProposeDeadlineRequest request) {
        TaskTransitionResult result = proposeDeadlineUseCase.execute(
                new ProposeDeadlineCommand(TaskId.of(id), request.proposedDeadline(), request.reason()));
        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @PostMapping("/{id}/deadline-proposals/decision")
    @PreAuthorize("hasAuthority('TASK_DECIDE_DEADLINE')")
    @Operation(
            summary = "Answer a proposed deadline",
            description = "TASK-DECIDE-DEADLINE-01. Requires TASK_DECIDE_DEADLINE at this endpoint and, in the "
                    + "use case, that the caller assigned the work or is the owner — a different manager cannot "
                    + "decide another's proposal. Keeping the decision here is what makes offering a proposal "
                    + "safe: the assignee gains a voice without gaining unilateral control of the date. "
                    + "Accepting moves the deadline, closes the proposal, records old and new values and "
                    + "re-evaluates at risk against the window in force at that moment. Declining leaves the "
                    + "deadline untouched and requires a reason, because the assignee raised a real constraint "
                    + "and is owed an answer to it. A date that passed while the proposal sat undecided is "
                    + "refused as stale rather than accepted, since agreeing it would create a task overdue at "
                    + "the instant somebody agreed.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The task, with the new deadline if it was agreed"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID when no outcome was given, or when a refusal carries no reason",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED, or NOT_THE_CREATOR when somebody else assigned the work",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "NO_OPEN_PROPOSAL when nothing is awaiting an answer",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "PROPOSAL_IS_STALE when the proposed date has passed",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskResponse> decideDeadline(
            @PathVariable UUID id, @Valid @RequestBody DecideDeadlineRequest request) {
        TaskTransitionResult result = decideDeadlineUseCase.execute(
                new DecideDeadlineCommand(TaskId.of(id), request.accept(), request.reason()));
        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TASK_EDIT')")
    @Operation(
            summary = "Correct what the task asks for",
            description = "TASK-EDIT-01. Requires TASK_EDIT at this endpoint and, in the use case, that the "
                    + "caller assigned the work or is the owner: another manager cannot retarget work they did "
                    + "not assign. **A deadline change is never silent** — it records old and new values, "
                    + "notifies the assignee because they may be pacing against the date, and re-evaluates at "
                    + "risk. Editing changes no state and touches no phase timer: a task in progress keeps its "
                    + "active phase running, because this is metadata and not progress. The title and the "
                    + "assignee cannot be changed here and the body carries neither — the title is the task's "
                    + "identity in every list and every notification already sent, and moving work to somebody "
                    + "else is TASK-REASSIGN-01's act. A closed task is refused, because it is a historical "
                    + "record. Submitting the values it already has is refused and appends nothing, since an "
                    + "event recording a change that did not happen is a false row.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The task, as it now reads"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID when the deadline or the priority is missing",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED, or NOT_THE_CREATOR when somebody else assigned the work",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "TASK_IS_CLOSED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "DEADLINE_IN_THE_PAST, or NOTHING_CHANGED when the values match what it already says",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskResponse> edit(@PathVariable UUID id, @Valid @RequestBody EditTaskRequest request) {
        TaskTransitionResult result = editTaskUseCase.execute(
                new EditTaskCommand(TaskId.of(id), request.deadline(), request.priority(), request.description()));
        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAuthority('TASK_VIEW_OWN')")
    @Operation(
            summary = "Leave a note on the work",
            description = "TASK-COMMENT-01. **There is no permission of its own**, and the annotation here is "
                    + "the weakest one every role holds: the precondition is view access, which the use case "
                    + "answers against the same scope rule the task detail uses. A TASK_COMMENT permission "
                    + "would be a second and weaker copy of that rule, and the day the two disagreed the "
                    + "product would show somebody a task they could not talk about with no way to explain "
                    + "why. Deliberately not a chat: no threading, no reactions, and **no editing or deleting "
                    + "after posting** — a correction is a new comment, because editing would let the record "
                    + "be rewritten after others have read and acted on it. A closed task is refused: it is a "
                    + "historical record, and a comment added afterwards sits outside the story it belongs to. "
                    + "The assignee and the creator are told, unless they are the author. Nothing about the "
                    + "task moves and no phase timer is touched.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The comment, as recorded"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID when the body is blank",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED, or TASK_OUT_OF_SCOPE when the work is outside your team",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "TASK_IS_CLOSED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskCommentResponse> comment(
            @PathVariable UUID id, @Valid @RequestBody CommentOnTaskRequest request) {
        TaskComment comment = commentOnTaskUseCase.execute(TaskId.of(id), request.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(comment));
    }

    @GetMapping("/{id}/activity")
    @PreAuthorize("hasAuthority('TASK_VIEW_OWN')")
    @Operation(
            summary = "Everything that happened to this task, in order",
            description = "TASK-COMMENT-01 criterion 5. **One chronology, not two lists**: the transitions and "
                    + "the comments interleave, because the reason a task was blocked is nearly always in the "
                    + "discussion around the block rather than in the block itself. Scoped by the same rule "
                    + "the task detail uses. A transition the owner forced past the state machine carries "
                    + "`overridden: true` and is rendered differently — a record where an override is "
                    + "indistinguishable from an ordinary move is a record that hides its own exceptions "
                    + "(TASK-OVERRIDE-01 extension 3a). An erased author's name comes back empty and the "
                    + "screen renders that as Former member; **the words of their comment are unchanged**, "
                    + "which is the stated bound of DECISION-ERASURE-BOUND-01. Writes nothing, tells nobody.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The activity, oldest first"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED, or TASK_OUT_OF_SCOPE when the work is outside your team",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskActivityResponse> activity(@PathVariable UUID id) {
        return ResponseEntity.ok(mapper.toResponse(viewTaskActivityUseCase.execute(TaskId.of(id))));
    }

    @PostMapping("/{id}/reassign")
    @PreAuthorize("hasAuthority('TASK_REASSIGN')")
    @Operation(
            summary = "Move the work to a different person",
            description = "TASK-REASSIGN-01. Requires TASK_REASSIGN at this endpoint — held by the owner and "
                    + "by managers — and, in the use case, that the new assignee is active and **within the "
                    + "caller's reporting scope**, read live from the tree so a reporting-line change takes "
                    + "effect immediately. **The state returns to Created and acceptance is required again**: "
                    + "the new person has not seen the task, and leaving it InProgress would assert they are "
                    + "working on something they do not know exists. **The previous assignee's accrued hours "
                    + "stay theirs** — their active and blocked rows remain closed and attributed to them, and "
                    + "nothing is transferred or reattributed. A reason is required, because the person who "
                    + "lost the work is owed one. A Completed task is refused: work awaiting review is "
                    + "finished, and if it is wrong it is returned through TASK-REJECT-IN-REVIEW-01 first. "
                    + "Non-delegable — reassignment directs labour rather than approving work.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The task, now Created and with the new assignee"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID when no reason or no person was given",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(responseCode = "403", description = "NOT_PERMITTED", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "TASK_IS_CLOSED, or ILLEGAL_TRANSITION when the work is Completed",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "ASSIGNEE_NOT_ACTIVE, ASSIGNEE_OUT_OF_SCOPE, or NOTHING_CHANGED when the person "
                        + "named already holds it",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskResponse> reassign(
            @PathVariable UUID id, @Valid @RequestBody ReassignTaskRequest request) {
        TaskTransitionResult result =
                reassignTaskUseCase.execute(TaskId.of(id), PersonId.of(request.newAssigneeId()), request.reason());
        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @PostMapping("/{id}/override")
    @PreAuthorize("hasAuthority('TASK_OVERRIDE')")
    @Operation(
            summary = "Force a state the machine would not allow",
            description = "TASK-OVERRIDE-01 — the escape hatch, and **the only route out of Closed in the "
                    + "product**. It is why Closed can be terminal in the normal machine without being a trap. "
                    + "Requires TASK_OVERRIDE, which V41 grants to **OWNER and to nobody else** and which V20 "
                    + "declares non-delegable; both properties live in the permission table rather than in a "
                    + "role comparison here that could disagree with it. Three things stop this becoming the "
                    + "normal path around an inconvenient machine: owner-only, **a required reason**, and a "
                    + "transition **permanently marked as an override** so the record cannot hide its own "
                    + "exceptions. Phase timers are closed and opened honestly for the new state, never "
                    + "skipped — exactly one is open afterwards, or none if the new state is Closed. Forcing "
                    + "the state the task is already in is refused and appends nothing. Never silent: the "
                    + "assignee and the creator are both told.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The task, in the state that was forced"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID when no reason or no target state was given",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED — every role but the owner, including a manager",
                content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "NOTHING_CHANGED when the target is the state it is already in",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskResponse> override(
            @PathVariable UUID id, @Valid @RequestBody OverrideTaskRequest request) {
        TaskTransitionResult result =
                overrideTaskUseCase.execute(TaskId.of(id), request.targetState(), request.reason());
        return ResponseEntity.ok(mapper.toResponse(result));
    }
}
