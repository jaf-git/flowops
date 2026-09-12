package com.flowops.process.api;

import com.flowops.process.api.dto.AbandonInstanceRequest;
import com.flowops.process.api.dto.AddTaskRequest;
import com.flowops.process.api.dto.AssignStepRequest;
import com.flowops.process.api.dto.AssignablePeopleResponse;
import com.flowops.process.api.dto.AttachableTasksResponse;
import com.flowops.process.api.dto.CloseInstanceRequest;
import com.flowops.process.api.dto.DependencyRequest;
import com.flowops.process.api.dto.InstanceListResponse;
import com.flowops.process.api.dto.InstanceResponse;
import com.flowops.process.api.dto.InstantiateRequest;
import com.flowops.process.api.dto.ReorderTasksRequest;
import com.flowops.process.api.dto.StartFromDescriptionsRequest;
import com.flowops.process.api.dto.StartFromTasksRequest;
import com.flowops.process.api.mapper.ProcessInstanceDtoMapper;
import com.flowops.process.application.abandoninstance.AbandonInstanceUseCase;
import com.flowops.process.application.addtask.AddTaskToInstanceUseCase;
import com.flowops.process.application.addtask.ViewAttachableTasksUseCase;
import com.flowops.process.application.archiveinstance.ArchiveInstanceUseCase;
import com.flowops.process.application.assignstep.AssignStepCommand;
import com.flowops.process.application.assignstep.AssignStepUseCase;
import com.flowops.process.application.closeinstance.CloseInstanceUseCase;
import com.flowops.process.application.editinstance.EditInstanceGraphUseCase;
import com.flowops.process.application.instantiate.InstantiateCommand;
import com.flowops.process.application.instantiate.InstantiateUseCase;
import com.flowops.process.application.skipstep.SkipStepUseCase;
import com.flowops.process.application.startfromtasks.StartFromDescriptionsCommand;
import com.flowops.process.application.startfromtasks.StartFromDescriptionsUseCase;
import com.flowops.process.application.startfromtasks.StartFromTasksCommand;
import com.flowops.process.application.startfromtasks.StartFromTasksUseCase;
import com.flowops.process.application.viewassignable.ViewAssignableForStepUseCase;
import com.flowops.process.application.viewinstance.InstancePopulation;
import com.flowops.process.application.viewinstance.ViewInstanceUseCase;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.StepDependency;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TaskRef;
import com.flowops.process.domain.model.TemplateId;
import com.flowops.shared.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/process-instances")
@Tag(
        name = "Process instances",
        description = "A run of a process, from the moment it starts to the moment every step is closed.")
public class ProcessInstanceController {
    private final InstantiateUseCase instantiateUseCase;
    private final StartFromTasksUseCase startFromTasksUseCase;
    private final StartFromDescriptionsUseCase startFromDescriptionsUseCase;
    private final AssignStepUseCase assignStepUseCase;
    private final SkipStepUseCase skipStepUseCase;
    private final ViewInstanceUseCase viewInstanceUseCase;
    private final ViewAssignableForStepUseCase viewAssignableForStepUseCase;
    private final AddTaskToInstanceUseCase addTaskToInstanceUseCase;
    private final ViewAttachableTasksUseCase viewAttachableTasksUseCase;
    private final EditInstanceGraphUseCase editInstanceGraphUseCase;
    private final AbandonInstanceUseCase abandonInstanceUseCase;
    private final ArchiveInstanceUseCase archiveInstanceUseCase;
    private final CloseInstanceUseCase closeInstanceUseCase;
    private final ProcessInstanceDtoMapper mapper;

    public ProcessInstanceController(
            AbandonInstanceUseCase abandonInstanceUseCase,
            ArchiveInstanceUseCase archiveInstanceUseCase,
            CloseInstanceUseCase closeInstanceUseCase,
            InstantiateUseCase instantiateUseCase,
            StartFromTasksUseCase startFromTasksUseCase,
            StartFromDescriptionsUseCase startFromDescriptionsUseCase,
            AssignStepUseCase assignStepUseCase,
            SkipStepUseCase skipStepUseCase,
            ViewInstanceUseCase viewInstanceUseCase,
            ViewAssignableForStepUseCase viewAssignableForStepUseCase,
            AddTaskToInstanceUseCase addTaskToInstanceUseCase,
            ViewAttachableTasksUseCase viewAttachableTasksUseCase,
            EditInstanceGraphUseCase editInstanceGraphUseCase,
            ProcessInstanceDtoMapper mapper) {
        this.abandonInstanceUseCase = abandonInstanceUseCase;
        this.archiveInstanceUseCase = archiveInstanceUseCase;
        this.closeInstanceUseCase = closeInstanceUseCase;
        this.instantiateUseCase = instantiateUseCase;
        this.startFromTasksUseCase = startFromTasksUseCase;
        this.startFromDescriptionsUseCase = startFromDescriptionsUseCase;
        this.assignStepUseCase = assignStepUseCase;
        this.skipStepUseCase = skipStepUseCase;
        this.viewInstanceUseCase = viewInstanceUseCase;
        this.viewAssignableForStepUseCase = viewAssignableForStepUseCase;
        this.addTaskToInstanceUseCase = addTaskToInstanceUseCase;
        this.viewAttachableTasksUseCase = viewAttachableTasksUseCase;
        this.editInstanceGraphUseCase = editInstanceGraphUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROCESS_INSTANTIATE')")
    @Operation(
            summary = "Start a run of a process, and name who steers it",
            description = "PROCESS-INSTANTIATE-01. Requires PROCESS_INSTANTIATE at this endpoint, which is "
                    + "non-delegable. The template is **copied** into the instance and never linked to it again "
                    + "(DECISION-PROCESS-SNAPSHOT-01), its graph is re-validated because it may have been "
                    + "edited since authoring, every entry step becomes reachable at once, and the whole of "
                    + "that commits together. The Process Owner may be an employee — it is a per-instance "
                    + "position, not a role.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The instance, with every step and its condition"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold PROCESS_INSTANTIATE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TEMPLATE_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "GRAPH_STEP_STRANDED or GRAPH_CYCLE: the template was edited into an invalid "
                        + "graph since it was authored, and no instance is created",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "PROCESS_OWNER_NOT_ACTIVE: an instance needs somebody who can steer it",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InstanceResponse> start(@Valid @RequestBody InstantiateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toResponse(instantiateUseCase.execute(new InstantiateCommand(
                        TemplateId.of(request.templateId()), request.name(), PersonId.of(request.processOwnerId())))));
    }

    @GetMapping("/attachable-tasks")
    @PreAuthorize("hasAuthority('PROCESS_INSTANTIATE')")
    @Operation(
            summary = "Which tasks a process could be started from",
            description = "Serves the start dialog for PROCESS-START-FROM-TASKS-01. The same list "
                    + "`/{id}/attachable-tasks` returns — the tasks this caller may see which belong to no run — "
                    + "for a run that does not exist yet, so there is no identifier to scope it by. **Scoped by "
                    + "TASK's own rule and by nothing here**, and it is a courtesy rather than the rule: the "
                    + "start endpoint applies the same checks to whatever identifiers a caller posts.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The tasks that could go into a new process"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold PROCESS_INSTANTIATE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AttachableTasksResponse> attachableTasksForANewRun() {
        return ResponseEntity.ok(new AttachableTasksResponse(viewAttachableTasksUseCase.forAStartingRun().stream()
                .map(task -> new AttachableTasksResponse.Row(
                        task.id(), task.title(), task.state(), task.assigneeId(), task.deadline()))
                .toList()));
    }

    @PostMapping("/from-tasks")
    @PreAuthorize("hasAuthority('PROCESS_INSTANTIATE')")
    @Operation(
            summary = "Start a run out of tasks that already exist, with no template",
            description = "PROCESS-START-FROM-TASKS-01. Requires PROCESS_INSTANTIATE at this endpoint, which is "
                    + "non-delegable. **Nothing is created and nothing is copied**: each named task joins the run "
                    + "keeping its assignee, its deadline, its state and its history, and nobody is notified "
                    + "because nobody was given anything. The run has **no template** — `templateId` is null in "
                    + "the response — and every other use case works on it unchanged. The order given is the "
                    + "**reading** order and draws no dependencies; what waits on what is said afterwards through "
                    + "PROCESS-REORDER-TASKS-01.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The run, with one step per task and no template"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID: a blank name, or no tasks at all — a run may not be born empty",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold PROCESS_INSTANTIATE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TASK_NOT_FOUND: a named task does not exist, or is outside the caller's scope. "
                        + "The two answer alike on purpose",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "TASK_ALREADY_IN_A_PROCESS: a named task is in a run already, or was named twice",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "PROCESS_OWNER_NOT_ACTIVE: a run needs somebody who can steer it",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InstanceResponse> startFromTasks(@Valid @RequestBody StartFromTasksRequest request) {
        List<TaskRef> tasks = new ArrayList<>();
        for (UUID task : request.taskIds()) {
            tasks.add(TaskRef.of(task));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toResponse(startFromTasksUseCase.execute(
                        new StartFromTasksCommand(request.name(), PersonId.of(request.processOwnerId()), tasks))));
    }

    @PostMapping("/from-descriptions")
    @PreAuthorize("hasAuthority('PROCESS_INSTANTIATE')")
    @Operation(
            summary = "Start a run from described work (PROCESS-START-FROM-DESCRIPTIONS-01)",
            description = "The third way a run begins, and the one for work that does not exist yet. The"
                    + " tasks and the run are created in a single transaction: doing it from a browser"
                    + " would be a create per step and then a start, and the caller whose third request"
                    + " fails would be left holding orphan tasks and no run — an arrangement"
                    + " DECISION-PROCESS-FROM-TASKS-01 examined and refused. Each task is created through"
                    + " TASK's ordinary create path, so the rule about whom the caller may give work to"
                    + " is applied by TASK, against the caller, and a step they may not direct takes the"
                    + " whole run down with it.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The run, with a step and a task per description"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID: no steps, a blank title, or no assignee",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "ASSIGNEE_OUT_OF_SCOPE: a step names somebody the caller may not give work to",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "PROCESS_OWNER_NOT_ACTIVE: a run needs somebody who can steer it",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InstanceResponse> startFromDescriptions(
            @Valid @RequestBody StartFromDescriptionsRequest request) {
        List<StartFromDescriptionsCommand.NewStep> steps = new ArrayList<>();
        for (StartFromDescriptionsRequest.Step step : request.steps()) {
            steps.add(new StartFromDescriptionsCommand.NewStep(
                    step.title(),
                    step.description(),
                    PersonId.of(step.assigneeId()),
                    step.deadline(),
                    step.priority() == null ? "NORMAL" : step.priority()));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toResponse(startFromDescriptionsUseCase.execute(new StartFromDescriptionsCommand(
                        request.name(), PersonId.of(request.processOwnerId()), steps))));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROCESS_VIEW_OWN')")
    @Operation(
            summary = "The runs this caller may see",
            description = "PROCESS-VIEW-INSTANCE-01. Scope is computed from the reporting tree **at the moment "
                    + "of the read**, so a reporting-line change takes effect immediately. Viewing notifies "
                    + "nobody.\n\n"
                    + "`population` chooses **which runs**, never **whose**: the visibility rule is the same "
                    + "for both values. `ON_THE_BOARD` is what the operations board draws and omits runs "
                    + "somebody put away (PROCESS-ARCHIVE-INSTANCE-01); `EVERY_RUN` includes them and is what "
                    + "any figure must be computed over, because archiving changes no history. It defaults to "
                    + "the board so that a caller asking for a board gets one.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Every instance in scope, newest first"),
        @ApiResponse(responseCode = "400", description = "population is not one of the two values", content = @Content),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InstanceListResponse> mine(
            @Parameter(description = "Which runs to answer with. Defaults to the board.")
                    @RequestParam(name = "population", defaultValue = "ON_THE_BOARD")
                    InstancePopulation population) {
        return ResponseEntity.ok(mapper.toList(viewInstanceUseCase.visible(population)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PROCESS_VIEW_OWN')")
    @Operation(
            summary = "Where one run stands",
            description =
                    "PROCESS-VIEW-INSTANCE-01. Returns every step with its condition and, once assigned, what TASK says about "
                            + "the task behind it: its state, who holds it now, when it is due, "
                            + "whether it is at risk, why it is held up, and time per phase -- never "
                            + "as a total. Every dependency is listed with whether the step it waits "
                            + "on has closed. Progress is steps closed against steps total, the steps "
                            + "awaiting assignment are their own field rather than something to filter "
                            + "for, and the bottleneck is a step and a duration. No field anywhere is "
                            + "keyed to a person"
                            + "steps awaiting assignment as a **first-class field**, and the bottleneck **as a step**. "
                            + "It returns no per-person aggregate of any kind, and no route exists that would produce "
                            + "one (DECISION-PROCESS-BOTTLENECK-01).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The instance, whole"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "INSTANCE_NOT_FOUND — **and the same response is returned when the instance "
                        + "exists and no permission of the caller's covers it**, so a refusal does not reveal "
                        + "that it exists (UC-07 extension 2a)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InstanceResponse> one(@PathVariable UUID id) {
        return ResponseEntity.ok(mapper.toResponse(viewInstanceUseCase.one(InstanceId.of(id))));
    }

    @PostMapping("/{id}/steps/{stepId}/assignment")
    @PreAuthorize("hasAuthority('PROCESS_ASSIGN_STEP') or @processOwnership.ownsInstance(#id)")
    @Operation(
            summary = "Hand a reachable step to somebody",
            description = "PROCESS-ASSIGN-REACHABLE-01, and the hinge of the feature. **The permission "
                    + "check is a disjunction because the Process Owner is a position and not a role**: an "
                    + "employee steering this run holds PROCESS_ASSIGN_STEP for this run and nothing "
                    + "workspace-wide, so a bare hasAuthority would refuse exactly the person this use case "
                    + "is written for (PROCESS_02 §4). It is the only delegable permission in the feature. "
                    + "A Task is created through TASK's ordinary create path, carrying the step's title and "
                    + "description, the two provenance facts, the chosen assignee and deadline — and the "
                    + "**instantiator** as its creator, so TASK's subtree rule runs against the person who "
                    + "authorised the run rather than the one steering it. The task and the step move in one "
                    + "transaction: a task created without its step marked assigned would be re-assigned on "
                    + "the next pass, producing duplicate work. From here TASK owns it entirely.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The instance, with the step now assigned to a task"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller neither holds PROCESS_ASSIGN_STEP nor steers this run",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "INSTANCE_NOT_FOUND, or UNKNOWN_STEP when the step is not part of this run",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "STEP_NOT_REACHABLE, naming the dependencies not yet closed; or "
                        + "ILLEGAL_STEP_TRANSITION carrying the condition the step was actually in",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "ASSIGNEE_NOT_ACTIVE, or ASSIGNEE_OUT_OF_SCOPE when the assignee sits outside "
                        + "the instantiator's part of the reporting tree",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InstanceResponse> assign(
            @PathVariable UUID id, @PathVariable UUID stepId, @Valid @RequestBody AssignStepRequest request) {
        assignStepUseCase.execute(new AssignStepCommand(
                InstanceId.of(id), StepId.of(stepId), PersonId.of(request.assigneeId()), request.deadline()));

        return ResponseEntity.ok(mapper.toResponse(viewInstanceUseCase.one(InstanceId.of(id))));
    }

    @PostMapping("/{id}/steps/{stepId}/skip")
    @PreAuthorize("hasAuthority('PROCESS_ASSIGN_STEP') or @processOwnership.ownsInstance(#id)")
    @Operation(
            summary = "This optional step does not apply to this run",
            description = "SOP-METADATA-01, SOP_01 §5. **The same permission as assignment, because it is the "
                    + "same decision**: the run owner is the person deciding what this step becomes, and "
                    + "answering *no* is one of the two answers. The disjunction is there for the same reason "
                    + "assignment's is — the Process Owner is a position, not a role. "
                    + "**Asked when the step becomes reachable, never at instantiation** (where the condition "
                    + "is frequently unknowable and a guess is then wrong for the rest of the run) and never "
                    + "of the assignee (a step has no assignee until the question is answered). "
                    + "Skipping **closes** the step, so dependents release and a run whose remaining work was "
                    + "all optional completes — through the same transition a finished task runs, not a "
                    + "second one. `origin` is preserved and the skip is visible in history: a step skipped is "
                    + "a fact about the run, not an absence from it. "
                    + "Idempotent: a second press changes nothing and appends no second event.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The step is closed, and whatever waited on it is open"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller neither holds PROCESS_ASSIGN_STEP nor steers this run",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "INSTANCE_NOT_FOUND, or UNKNOWN_STEP when the step is not part of this run",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "STEP_NOT_OPTIONAL when the template says this step always applies — remove it "
                        + "from the run instead, which looks like what it is; or STEP_NOT_AWAITING_DECISION "
                        + "when the step is still pending or already assigned",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InstanceResponse> skip(@PathVariable UUID id, @PathVariable UUID stepId) {
        skipStepUseCase.skip(InstanceId.of(id), StepId.of(stepId));

        return ResponseEntity.ok(mapper.toResponse(viewInstanceUseCase.one(InstanceId.of(id))));
    }

    @GetMapping("/{id}/assignable-people")
    @PreAuthorize("hasAuthority('PROCESS_ASSIGN_STEP') or @processOwnership.ownsInstance(#id)")
    @Operation(
            summary = "Who a step of this run may be handed to",
            description = "Serves the assignment dialog for PROCESS-ASSIGN-REACHABLE-01. **The list is "
                    + "computed for the run's instantiator, not for the caller**, because the task created by "
                    + "an assignment names the instantiator as its creator (decision row 206) and TASK's "
                    + "subtree rule therefore runs against them. Computed for the caller it would offer the "
                    + "Process Owner people the next click refuses, and hide people it would accept. "
                    + "**It carries the assignment endpoint's own permission check verbatim**, so nobody "
                    + "learns a part of the reporting tree they could not already enumerate by attempting "
                    + "assignments into it. The list is a courtesy and never the rule: the assignment itself "
                    + "is still checked, and a name here can still be refused if the tree changed in between.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The people this run's steps may be assigned to"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller neither holds PROCESS_ASSIGN_STEP nor steers this run",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "INSTANCE_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AssignablePeopleResponse> assignablePeople(@PathVariable UUID id) {
        return ResponseEntity.ok(
                new AssignablePeopleResponse(viewAssignableForStepUseCase.execute(InstanceId.of(id)).stream()
                        .map(candidate -> new AssignablePeopleResponse.Candidate(
                                candidate.person().value(), candidate.displayName()))
                        .toList()));
    }

    @GetMapping("/{id}/attachable-tasks")
    @PreAuthorize("hasAuthority('PROCESS_EDIT_INSTANCE') or @processOwnership.ownsInstance(#id)")
    @Operation(
            summary = "Which of my tasks could go into this run",
            description = "Serves the picker for PROCESS-ADD-TASK-01. **Scope is TASK's answer, called rather "
                    + "than reimplemented**: the list is exactly the tasks the caller may see, minus the ones "
                    + "some run already holds. A second copy of who-may-see-what is the copy that stays "
                    + "faithful until one of the two is edited, and the edit that matters is the one that "
                    + "widens it. The list is a courtesy and never the rule — the add endpoint applies the "
                    + "same checks to whatever identifier is posted, offered or not. **An empty list is a "
                    + "valid answer** and the screen distinguishes its two causes: you can see no tasks at "
                    + "all, or everything you can see is already in a run.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tasks in the caller's scope that belong to no run"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller neither holds PROCESS_EDIT_INSTANCE nor steers this run",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "INSTANCE_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AttachableTasksResponse> attachableTasks(@PathVariable UUID id) {
        return ResponseEntity.ok(
                new AttachableTasksResponse(viewAttachableTasksUseCase.forRun(InstanceId.of(id)).stream()
                        .map(task -> new AttachableTasksResponse.Row(
                                task.id(), task.title(), task.state(), task.assigneeId(), task.deadline()))
                        .toList()));
    }

    @PostMapping("/{id}/tasks")
    @PreAuthorize("hasAuthority('PROCESS_EDIT_INSTANCE') or @processOwnership.ownsInstance(#id)")
    @Operation(
            summary = "Put a task into a run that is already going",
            description = "PROCESS-ADD-TASK-01. Two doors, **and their checks differ because the acts "
                    + "differ**. Supplying `taskId` *attaches* a task that already exists: its assignee, "
                    + "deadline and state are untouched, so it gives nobody work and the only question is "
                    + "whether the caller may see it. Supplying the new-task fields *creates* one through "
                    + "TASK's ordinary create path, which does give somebody work and therefore keeps every "
                    + "check that path makes, subtree check included. Exactly one of the two shapes is "
                    + "supplied. An attached task that is already Closed makes a closed step and can unlock "
                    + "whatever waited for it in the same transaction. **A task belongs to at most one run**, "
                    + "which a unique index enforces as well as this endpoint — a service check alone is not "
                    + "a guarantee under a race. No cycle can form here: a step being added has nothing "
                    + "depending on it yet.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The run, with the task now in it"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID: neither shape was supplied, or both were",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "INSTANCE_NOT_FOUND; or TASK_NOT_FOUND — **returned identically for a task "
                        + "outside the caller's scope and for one that does not exist**, so this endpoint "
                        + "cannot be used to learn which identifiers name real work (UC-12 extension 2b)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "TASK_ALREADY_IN_A_PROCESS, or INSTANCE_NOT_RUNNING on a finished run",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_STEP when a runs-after names a step of another run; "
                        + "ASSIGNEE_NOT_ACTIVE or ASSIGNEE_OUT_OF_SCOPE on the create door",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InstanceResponse> addTask(@PathVariable UUID id, @Valid @RequestBody AddTaskRequest request) {
        addTaskToInstanceUseCase.execute(mapper.toAddCommand(InstanceId.of(id), request));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toResponse(viewInstanceUseCase.one(InstanceId.of(id))));
    }

    @DeleteMapping("/{id}/tasks/{stepId}")
    @PreAuthorize("hasAuthority('PROCESS_EDIT_INSTANCE') or @processOwnership.ownsInstance(#id)")
    @Operation(
            summary = "Take a task out of a run, without destroying it",
            description = "PROCESS-REMOVE-TASK-01. The step and every edge touching it leave the run; **the "
                    + "task survives** with the same state, the same assignee, the same deadline and the same "
                    + "place in that person's queue, losing only the two provenance facts. PROCESS holds no "
                    + "port that could do more — the one that writes provenance has two methods and neither "
                    + "takes a state, a date or a person. Removing a step other steps waited on makes them "
                    + "reachable **in the same transaction**, and the Process Owner is told. The last step of "
                    + "a run cannot be removed: a run with no work in it is not a run.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The run without it, showing which dependents opened"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "INSTANCE_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "INSTANCE_NEEDS_A_TASK on the last step, or INSTANCE_NOT_RUNNING on a finished run",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_STEP when the step is not part of this run",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InstanceResponse> removeTask(@PathVariable UUID id, @PathVariable UUID stepId) {
        editInstanceGraphUseCase.remove(InstanceId.of(id), StepId.of(stepId));
        return ResponseEntity.ok(mapper.toResponse(viewInstanceUseCase.one(InstanceId.of(id))));
    }

    @PatchMapping("/{id}/tasks/order")
    @PreAuthorize("hasAuthority('PROCESS_EDIT_INSTANCE') or @processOwnership.ownsInstance(#id)")
    @Operation(
            summary = "Change the order the run reads in",
            description = "PROCESS-REORDER-TASKS-01. **Reading order, never running order.** Position decides "
                    + "how a run is listed and laid out; the dependency graph decides when work may begin, and "
                    + "it has its own endpoint. Nothing here touches a condition, a task or an edge — a move "
                    + "that quietly rewired the graph would change when somebody's work may start as a side "
                    + "effect of tidying a list. The body carries **every** step of the run: a partial order "
                    + "would leave two steps sharing a position, and a list whose sequence then depends on "
                    + "which row the database returns first is one that changes under a person while they read "
                    + "it.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The run, in the new order"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "INSTANCE_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "INSTANCE_NOT_RUNNING",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_STEP: the order is not exactly this run's steps",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InstanceResponse> reorderTasks(
            @PathVariable UUID id, @Valid @RequestBody ReorderTasksRequest request) {
        editInstanceGraphUseCase.reorder(
                InstanceId.of(id), request.stepIds().stream().map(StepId::of).toList());
        return ResponseEntity.ok(mapper.toResponse(viewInstanceUseCase.one(InstanceId.of(id))));
    }

    @PostMapping("/{id}/dependencies")
    @PreAuthorize("hasAuthority('PROCESS_EDIT_INSTANCE') or @processOwnership.ownsInstance(#id)")
    @Operation(
            summary = "Say that one step of a run waits for another",
            description = "PROCESS-REORDER-TASKS-01, the running-order half. Validated by the **same** "
                    + "DependencyGraph a template is validated by, so a cycle is refused with every step of "
                    + "the cycle named. Drawing an edge onto a step nobody has taken returns it to pending, "
                    + "because it now waits for something unfinished; **an assigned step is left alone**, "
                    + "since somebody holds that task and PROCESS has no authority over it. Drawing the same "
                    + "edge twice succeeds, changes nothing and appends no second event.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The run with its updated graph"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "INSTANCE_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "GRAPH_CYCLE naming every step of the cycle, or INSTANCE_NOT_RUNNING",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_STEP when an end of the edge is not part of this run",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InstanceResponse> drawDependency(
            @PathVariable UUID id, @Valid @RequestBody DependencyRequest request) {
        editInstanceGraphUseCase.draw(
                InstanceId.of(id),
                new StepDependency(StepId.of(request.dependentStepId()), StepId.of(request.dependsOnStepId())));
        return ResponseEntity.ok(mapper.toResponse(viewInstanceUseCase.one(InstanceId.of(id))));
    }

    @DeleteMapping("/{id}/dependencies/{dependentStepId}/{dependsOnStepId}")
    @PreAuthorize("hasAuthority('PROCESS_EDIT_INSTANCE') or @processOwnership.ownsInstance(#id)")
    @Operation(
            summary = "Take a dependency back out of a run",
            description = "PROCESS-REORDER-TASKS-01. Erasing the last unmet dependency of a pending step makes "
                    + "it reachable in the same transaction and tells the Process Owner it is their move — "
                    + "otherwise the work would open silently, which is the invisible stall the feature exists "
                    + "to prevent, arriving through a new door. Erasing an edge that is not drawn succeeds and "
                    + "changes nothing, for the same reason drawing one twice does.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The run with its updated graph"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "INSTANCE_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "GRAPH_STEP_STRANDED, or INSTANCE_NOT_RUNNING",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_STEP",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InstanceResponse> eraseDependency(
            @PathVariable UUID id, @PathVariable UUID dependentStepId, @PathVariable UUID dependsOnStepId) {
        editInstanceGraphUseCase.erase(
                InstanceId.of(id), new StepDependency(StepId.of(dependentStepId), StepId.of(dependsOnStepId)));
        return ResponseEntity.ok(mapper.toResponse(viewInstanceUseCase.one(InstanceId.of(id))));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('PROCESS_ARCHIVE_INSTANCE')")
    @Operation(
            summary = "Put a finished run away, so the board stops carrying it",
            description = "PROCESS-ARCHIVE-INSTANCE-01. **A statement about a screen, never about the work.** "
                    + "Nothing closes, nothing is deleted, no task is touched, and every figure the analysis "
                    + "and export surfaces compute counts an archived run exactly as it counted it before — "
                    + "`archived_at` is read by the operations board and by nothing else. It exists because a "
                    + "workspace that has been busy for a year accumulates finished runs until the board "
                    + "cannot be read. **Reversible** through DELETE on the same path. A run that is still "
                    + "RUNNING is refused: archiving live work would hide it from the only screen that shows "
                    + "it, and the schema holds that rule as a check constraint so no other door can get it "
                    + "wrong quietly. Requires PROCESS_ARCHIVE_INSTANCE rather than PROCESS_EDIT_INSTANCE, "
                    + "because editing a run changes it for the people working it while archiving removes it "
                    + "from everybody's board.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "The run is off the board"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold PROCESS_ARCHIVE_INSTANCE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "No such run", content = @Content),
        @ApiResponse(
                responseCode = "409",
                description = "INSTANCE_STILL_RUNNING: a run that has not ended cannot be archived",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        archiveInstanceUseCase.archive(InstanceId.of(id));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('PROCESS_ARCHIVE_INSTANCE')")
    @Operation(
            summary = "Put an archived run back on the board",
            description = "PROCESS-ARCHIVE-INSTANCE-01, the reversal. Restoring a run that is already on the "
                    + "board is not an error — the request describes the state it wants rather than a "
                    + "transition it believes is available, so two clicks and one click agree.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "The run is back on the board"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold PROCESS_ARCHIVE_INSTANCE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "No such run", content = @Content)
    })
    public ResponseEntity<Void> restore(@PathVariable UUID id) {
        archiveInstanceUseCase.restore(InstanceId.of(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/abandonment")
    @PreAuthorize("hasAuthority('PROCESS_ABANDON_INSTANCE')")
    @Operation(
            summary = "Stop a run that should not continue",
            description = "PROCESS-ABANDON-INSTANCE-01. The run ends deliberately rather than being quietly "
                    + "forgotten, and **a reason is required** because everybody who had a task in it will "
                    + "read it. No pending step ever becomes reachable afterwards — leaving RUNNING is the "
                    + "whole mechanism, since every evaluation in the aggregate runs behind a state check. "
                    + "**Tasks already in flight are not closed by this**: PROCESS holds no port that mutates "
                    + "a task (PROCESS_00 §4), so the response lists their steps in `needingAttention` and a "
                    + "person ends each one through TASK-OVERRIDE-01, on purpose and one at a time. That use "
                    + "case existing is what made this one buildable — before it, an assigned task could not "
                    + "be ended at all and this list would have named work nobody could act on. A run that has "
                    + "already finished, or already been abandoned, is refused.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The run, abandoned, with its survivors listed"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID when no reason was given",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(responseCode = "403", description = "NOT_PERMITTED", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "INSTANCE_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "INSTANCE_NOT_RUNNING — it has already finished or already been stopped",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InstanceResponse> abandon(
            @PathVariable UUID id, @Valid @RequestBody AbandonInstanceRequest request) {
        abandonInstanceUseCase.execute(InstanceId.of(id), request.reason());

        return ResponseEntity.ok(mapper.toResponse(viewInstanceUseCase.one(InstanceId.of(id))));
    }

    @PostMapping("/{id}/closure")
    @PreAuthorize("hasAuthority('PROCESS_ABANDON_INSTANCE')")
    @Operation(
            summary = "Record that a run finished, with work still open on it",
            description = "PROCESS-CLOSE-INSTANCE-01, and the counterpart `/{id}/abandonment` was shipped "
                    + "without. A run reaches COMPLETE on its own the moment its last step closes, so the "
                    + "ordinary ending needs no endpoint. What had no door was the run that **succeeded** while "
                    + "a step nobody needs any more is still open — and with only abandonment to reach for, the "
                    + "way to record a success was to record it as a failure, which is a false statement about "
                    + "a real thing.\n\n"
                    + "**A note is required**, for the reason the abandonment reason is: everybody who held a "
                    + "task in the run will read it, and several of them are holding one that has just been "
                    + "declared unnecessary.\n\n"
                    + "**Tasks already in flight are not closed by this.** PROCESS holds no port that mutates a "
                    + "task (PROCESS_00 §4), so the response lists their steps in `needingAttention` exactly as "
                    + "an abandonment does, and a person ends each one through TASK-OVERRIDE-01.\n\n"
                    + "Requires PROCESS_ABANDON_INSTANCE — the same authority pointed at the other ending, "
                    + "because acting on a run that already exists is one reach and giving two neighbouring "
                    + "capabilities two role maps is how a permission table stops being readable. A run that "
                    + "has already finished, or already been abandoned, is refused.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The run, complete, with any survivors listed"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID when no note was given",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(responseCode = "403", description = "NOT_PERMITTED", content = @Content),
        @ApiResponse(
                responseCode = "404",
                description = "INSTANCE_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "INSTANCE_NOT_RUNNING — it has already finished or already been stopped",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InstanceResponse> close(
            @PathVariable UUID id, @Valid @RequestBody CloseInstanceRequest request) {
        closeInstanceUseCase.execute(InstanceId.of(id), request.note());

        return ResponseEntity.ok(mapper.toResponse(viewInstanceUseCase.one(InstanceId.of(id))));
    }
}
