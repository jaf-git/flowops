package com.flowops.tasklib.api;

import com.flowops.shared.web.ErrorResponse;
import com.flowops.tasklib.api.dto.BulkApprovalRequest;
import com.flowops.tasklib.api.dto.DraftCandidateResponse;
import com.flowops.tasklib.api.dto.ShapeCheckRequest;
import com.flowops.tasklib.api.dto.StampTaskRequest;
import com.flowops.tasklib.api.dto.TaskTemplateResponse;
import com.flowops.tasklib.api.dto.TemplateDraftRequest;
import com.flowops.tasklib.api.dto.TemplateLibraryResponse;
import com.flowops.tasklib.api.dto.TemplateMetadataRequest;
import com.flowops.tasklib.api.dto.TemplateRejectionRequest;
import com.flowops.tasklib.api.dto.TemplateSuggestionResponse;
import com.flowops.tasklib.api.dto.TemplateTaskResponse;
import com.flowops.tasklib.api.dto.TemplateUsageResponse;
import com.flowops.tasklib.application.TaskTemplateUseCase;
import com.flowops.tasklib.application.draftcandidates.ViewDraftCandidatesUseCase;
import com.flowops.tasklib.application.exception.NotTheAuthorException;
import com.flowops.tasklib.application.exception.TemplateNotFoundException;
import com.flowops.tasklib.application.exception.UnknownBandException;
import com.flowops.tasklib.application.metadata.TemplateMetadataUseCase;
import com.flowops.tasklib.application.port.TaskTemplatePort;
import com.flowops.tasklib.domain.MetadataField;
import com.flowops.tasklib.domain.TaskTemplate;
import com.flowops.tasklib.domain.TemplateDetails;
import com.flowops.tasklib.domain.TemplateStatus;
import com.flowops.tasklib.domain.exception.IllegalTemplateTransitionException;
import com.flowops.tasklib.domain.exception.UnknownMetadataValueException;
import com.flowops.tasklib.domain.shape.ProcessShapeHint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task-templates")
@Tag(name = "Task templates", description = "Work worth writing down once and using many times")
public class TaskTemplateController {
    private final TaskTemplateUseCase templates;
    private final ViewDraftCandidatesUseCase draftCandidates;
    private final TemplateMetadataUseCase templateMetadata;

    public TaskTemplateController(
            TaskTemplateUseCase templates,
            ViewDraftCandidatesUseCase draftCandidates,
            TemplateMetadataUseCase templateMetadata) {
        this.templates = templates;
        this.draftCandidates = draftCandidates;
        this.templateMetadata = templateMetadata;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_VIEW')")
    @Operation(
            summary = "The library",
            description = "TASKLIB-VIEW-LIBRARY-01. Requires TASK_TEMPLATE_VIEW. Approved templates and the "
                    + "caller's own drafts; somebody else's draft is never returned, because a draft is an "
                    + "unfinished thought rather than a published one. The types in use travel with the page "
                    + "so the filter chips cost no second request.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "One page of the library"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_TEMPLATE_VIEW",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TemplateLibraryResponse> library(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) TemplateStatus status,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(required = false) TaskTemplatePort.TemplateSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size) {
        int bounded = Math.min(Math.max(size, 1), 60);
        TaskTemplateUseCase.Library library = templates.search(new TaskTemplatePort.TemplateQuery(
                q,
                type,
                status,
                mine,
                sort == null ? TaskTemplatePort.TemplateSort.MOST_USED : sort,
                Math.max(page, 0),
                bounded));
        return ResponseEntity.ok(new TemplateLibraryResponse(
                library.templates().stream().map(this::described).toList(),
                templates.typesInUse(),
                library.total(),
                library.page(),
                library.size(),
                library.totalPages()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_VIEW')")
    @Operation(
            summary = "One template, by identifier",
            description = "TASKLIB-VIEW-LIBRARY-01. Requires TASK_TEMPLATE_VIEW. The analysis page reaches a "
                    + "template directly by its URL, so it cannot get the title out of a page of the library "
                    + "it never asked for — and searching the library for one row would answer nothing for a "
                    + "template the current filters exclude. Retired templates answer here too: the page that "
                    + "says a template is dead is exactly the page somebody opens about a dead one.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The template as written"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_TEMPLATE_VIEW",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "No such template", content = @Content)
    })
    public ResponseEntity<TaskTemplateResponse> byId(@PathVariable UUID id) {
        return ResponseEntity.ok(described(templates.byId(id)));
    }

    @GetMapping("/approval-queue")
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_APPROVE')")
    @Operation(
            summary = "Templates waiting on a decision",
            description = "TASKLIB-APPROVE-TEMPLATE-01. Requires TASK_TEMPLATE_APPROVE. Oldest first, so the "
                    + "proposal that has waited longest is the one answered first.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Everything proposed, oldest first"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_TEMPLATE_APPROVE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<TaskTemplateResponse>> approvalQueue() {
        return ResponseEntity.ok(
                templates.awaitingApproval().stream().map(this::described).toList());
    }

    @GetMapping("/draft-candidates")
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_APPROVE')")
    @Operation(
            summary = "The curation queue as jobs rather than as drafts",
            description = "TASKLIB-DRAFT-FROM-TASK-01, curation half. Requires TASK_TEMPLATE_APPROVE.\n\n"
                    + "Every ad-hoc task anybody types leaves a draft, so the raw queue is hundreds of "
                    + "rows describing a handful of distinct jobs. This groups them by normalised title — "
                    + "accents folded, case folded, whitespace collapsed — and answers one row per job, "
                    + "most-repeated first. `FND_REQ_DECISION_08` requires this to exist wherever "
                    + "auto-drafting does: twenty free-form tasks across four titles answer four rows.\n\n"
                    + "**It under-groups on purpose.** *Site survey* and *Survey the site* arrive as two "
                    + "rows. That fails toward more rows rather than wrong merges — two rows that should "
                    + "be one can be merged by whoever is looking, while one row that should be two has "
                    + "already lost the distinction silently.\n\n"
                    + "Nothing about a person is read. A template carries no assignee, so the same job "
                    + "done by two people is one row by construction rather than by care.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "One row per job, most-repeated first. Empty is normal"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_TEMPLATE_APPROVE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<DraftCandidateResponse>> draftCandidates() {
        return ResponseEntity.ok(draftCandidates.candidates().stream()
                .map(candidate -> new DraftCandidateResponse(
                        candidate.title(),
                        candidate.variants(),
                        candidate.drafts(),
                        candidate.firstSeen(),
                        candidate.lastSeen(),
                        candidate.templateIds()))
                .toList());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_CREATE')")
    @Operation(
            summary = "Write a template",
            description = "TASKLIB-PROPOSE-TEMPLATE-01. Requires TASK_TEMPLATE_CREATE, which everybody who can "
                    + "hold a task has. It lands as a draft or in the approval queue depending on the request; "
                    + "neither is usable until somebody who may approve has approved it.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The template as written"),
        @ApiResponse(responseCode = "400", description = "A template needs a title", content = @Content),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_TEMPLATE_CREATE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskTemplateResponse> create(@Valid @RequestBody TemplateDraftRequest request) {
        TaskTemplate created = templates.create(detailsOf(request), request.submitForApproval());
        return ResponseEntity.status(HttpStatus.CREATED).body(described(created));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_CREATE')")
    @Operation(
            summary = "Rewrite a template",
            description = "TASKLIB-EDIT-TEMPLATE-01. Requires TASK_TEMPLATE_CREATE at this endpoint and being "
                    + "the template's author in the use case — rewriting somebody else's approved template "
                    + "would be publishing under their name.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The template as rewritten"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(responseCode = "403", description = "Not this caller's template", content = @Content),
        @ApiResponse(responseCode = "404", description = "No such template", content = @Content)
    })
    public ResponseEntity<TaskTemplateResponse> edit(
            @PathVariable UUID id, @Valid @RequestBody TemplateDraftRequest request) {
        return ResponseEntity.ok(described(templates.edit(id, detailsOf(request), request.submitForApproval())));
    }

    @PostMapping("/{id}/approval")
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_APPROVE')")
    @Operation(
            summary = "Approve one",
            description = "TASKLIB-APPROVE-TEMPLATE-01. Requires TASK_TEMPLATE_APPROVE. Refused unless the "
                    + "template is actually waiting: approving twice is not a harmless repeat, it would let a "
                    + "retired template be brought back with a button meant for a queue.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Approved, and usable from now on"),
        @ApiResponse(responseCode = "409", description = "Not waiting for approval", content = @Content),
        @ApiResponse(responseCode = "404", description = "No such template", content = @Content)
    })
    public ResponseEntity<TaskTemplateResponse> approve(
            @PathVariable UUID id, @RequestBody(required = false) @Valid TemplateDraftRequest edited) {
        return ResponseEntity.ok(described(templates.approve(id, edited == null ? null : detailsOf(edited))));
    }

    @PostMapping("/approvals")
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_APPROVE')")
    @Operation(
            summary = "Approve several",
            description = "One transaction. A list holding one identifier that cannot be approved approves "
                    + "none of them, because a partial success leaves the person unable to tell what happened.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "All of them, approved"),
        @ApiResponse(responseCode = "409", description = "One of them was not waiting", content = @Content),
        @ApiResponse(responseCode = "404", description = "One of them does not exist", content = @Content)
    })
    public ResponseEntity<List<TaskTemplateResponse>> approveAll(@Valid @RequestBody BulkApprovalRequest request) {
        return ResponseEntity.ok(templates.approveAll(request.templateIds()).stream()
                .map(this::described)
                .toList());
    }

    @PostMapping("/{id}/rejection")
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_APPROVE')")
    @Operation(
            summary = "Send one back",
            description = "TASKLIB-APPROVE-TEMPLATE-01, the other branch. It returns to its author as a draft "
                    + "rather than being deleted, so what they wrote survives and can be reworked.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Back with its author, as a draft"),
        @ApiResponse(responseCode = "404", description = "No such template", content = @Content)
    })
    public ResponseEntity<TaskTemplateResponse> sendBack(
            @PathVariable UUID id, @Valid @RequestBody TemplateRejectionRequest request) {
        return ResponseEntity.ok(described(templates.sendBack(id, request.reason())));
    }

    @PostMapping("/{id}/retirement")
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_RETIRE')")
    @Operation(
            summary = "Withdraw one",
            description = "Requires TASK_TEMPLATE_RETIRE. The row is kept and stops being offered: a template "
                    + "that produced two hundred tasks is the provenance of all two hundred, and deleting it "
                    + "would orphan every one of them.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Retired, and still readable"),
        @ApiResponse(responseCode = "404", description = "No such template", content = @Content)
    })
    public ResponseEntity<TaskTemplateResponse> retire(@PathVariable UUID id) {
        return ResponseEntity.ok(described(templates.retire(id)));
    }

    @PatchMapping("/{id}/metadata")
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_METADATA')")
    @Operation(
            summary = "Record one thing this template says about the work",
            description = "SOP-METADATA-01. Requires TASK_TEMPLATE_METADATA, which V62 grants to owners and "
                    + "managers alone — SOP_01 section 4 says only they are asked, because a responsible role is "
                    + "an organisational decision and asking an employee for one is asking them to make it. That "
                    + "guarantee lives here rather than in a hidden component on a screen. "
                    + "One field per call: two questions at once is a form, and a form at the moment somebody is "
                    + "trying to create a task is friction they will route around by not using templates. "
                    + "Skipping sends no request at all and is recorded nowhere.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recorded, with the next question to ask if there is one"),
        @ApiResponse(
                responseCode = "400",
                description = "Not one of the six fields, or not a value it accepts",
                content = @Content),
        @ApiResponse(responseCode = "403", description = "An employee. They are asked nothing", content = @Content),
        @ApiResponse(responseCode = "404", description = "No such template", content = @Content)
    })
    public ResponseEntity<TaskTemplateResponse> recordMetadata(
            @PathVariable UUID id, @Valid @RequestBody TemplateMetadataRequest request) {
        return ResponseEntity.ok(
                described(templateMetadata.record(id, MetadataField.valueOf(request.field()), request.answer())));
    }

    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_CREATE')")
    @Operation(
            summary = "Copy one as your own draft",
            description = "For a template that nearly fits. The copy belongs to whoever asked and starts as a "
                    + "draft, so adjusting somebody else's work never changes theirs.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "A fresh draft, belonging to the caller"),
        @ApiResponse(responseCode = "404", description = "No such template", content = @Content)
    })
    public ResponseEntity<TaskTemplateResponse> copy(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(described(templates.copy(id)));
    }

    @PostMapping("/{id}/tasks")
    @PreAuthorize("hasAuthority('TASK_CREATE')")
    @Operation(
            summary = "Create work from this template",
            description = "TASKLIB-USE-TEMPLATE-01. Requires TASK_CREATE, because the result is a task "
                    + "and that is TASK's permission to give. The task is created through TASK's own "
                    + "create path and is TASK's entirely from that moment; this feature sets no state, "
                    + "moves no deadline and reassigns nobody. "
                    + "Creating the task and counting the use are one transaction: an earlier version "
                    + "counted the use and left the caller to create the task, so the library counted "
                    + "browsing and the screen announced work that did not exist. "
                    + "Only an approved template answers — a draft is somebody's unfinished thought and "
                    + "a retired one was withdrawn on purpose.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The task that now exists, and its provenance"),
        @ApiResponse(responseCode = "400", description = "No assignee", content = @Content),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_CREATE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "No such template", content = @Content),
        @ApiResponse(
                responseCode = "409",
                description = "TEMPLATE_STATE_REFUSES: it is not an approved template",
                content = @Content)
    })
    public ResponseEntity<TaskTemplateUseCase.StampedTask> stamp(
            @PathVariable UUID id, @Valid @RequestBody StampTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templates.stampTask(
                        id,
                        new TaskTemplateUseCase.StampRequest(
                                request.title(),
                                request.description(),
                                request.assigneeId(),
                                request.deadline(),
                                request.priority())));
    }

    @GetMapping("/{id}/usage")
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_VIEW')")
    @Operation(
            summary = "How this template has actually performed",
            description = "TASKLIB-VIEW-TEMPLATE-USAGE-01. Requires TASK_TEMPLATE_VIEW. Computed live "
                    + "from provenance, phase timers and approvals — nothing stored, nothing stale. "
                    + "Durations are active time only, so they say how long the work takes rather than "
                    + "how long it sat, and they are reported as a spread because a template whose tasks "
                    + "take two hours or twelve is telling you something an average erases. A thin "
                    + "sample is marked rather than hidden. "
                    + "**No figure on this route is keyed to a person and none can be**: the types carry "
                    + "no person identifier, so the query that would answer *who did these and how each "
                    + "of them did* cannot be written against it (DECISION-APPROVAL-SCORE-01).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The figures, and what its tasks are doing now"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_TEMPLATE_VIEW",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "No such template", content = @Content)
    })
    public ResponseEntity<TemplateUsageResponse> usage(@PathVariable UUID id) {
        TaskTemplateUseCase.Performance performance = templates.performanceOf(id);
        return ResponseEntity.ok(TemplateUsageResponse.of(
                performance.figures(),
                performance.live(),
                performance.template().details().estimatedHours()));
    }

    @GetMapping("/{id}/tasks")
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_VIEW')")
    @Operation(
            summary = "The work behind one of the usage counts",
            description = "TASKLIB-VIEW-TEMPLATE-USAGE-01. Requires TASK_TEMPLATE_VIEW. Returns the tasks a "
                    + "band's number counted, selected by **the same predicate that produced the number** — so a "
                    + "list and the count it was opened from cannot disagree. Bands are `not-started`, `running`, "
                    + "`blocked`, `in-review`, `finished` and `overdue`; anything else is refused rather than "
                    + "answered with an empty list, because *no overdue work* is the most reassuring wrong answer "
                    + "this page could give.\n\n"
                    + "**These rows name people and carry no figure, and that is the line.** Extension 2d refuses "
                    + "a figure keyed to a person — a duration or a pass rate attributed to a name. A row is one "
                    + "task and whoever holds it, exactly as the work queue shows it to anybody who may see the "
                    + "task at all, with nothing on it that could be summed into a judgement.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The tasks in that band, soonest deadline first"),
        @ApiResponse(
                responseCode = "400",
                description = "UNKNOWN_BAND: not one of the six the counts report",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_TEMPLATE_VIEW",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "No such template", content = @Content)
    })
    public ResponseEntity<List<TemplateTaskResponse>> tasksIn(@PathVariable UUID id, @RequestParam String band) {
        return ResponseEntity.ok(templates.tasksIn(id, band).stream()
                .map(TemplateTaskResponse::of)
                .toList());
    }

    @GetMapping("/suggestions")
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_VIEW')")
    @Operation(
            summary = "Templates that resemble what somebody is typing",
            description = "TASKLIB-SUGGEST-TEMPLATE-01. Requires TASK_TEMPLATE_VIEW. Approved templates "
                    + "only, most alike first, and nothing below a similarity floor — so an unrelated "
                    + "title returns an empty list rather than the least-bad match. Titles are compared "
                    + "with their diacritics removed, because somebody types `factura` and the template "
                    + "is `Verificare factura lunara`; without that the feature would silently never "
                    + "match in Romanian while working perfectly in English. It retrieves and asks "
                    + "nothing: no merge is proposed and no decision recorded, which is what keeps it on "
                    + "the right side of TASKLIB_00 section 5.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resembling templates, or none"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_TEMPLATE_VIEW",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<TemplateSuggestionResponse>> suggestions(
            @RequestParam String title, @RequestParam(defaultValue = "3") int limit) {
        return ResponseEntity.ok(templates.resembling(title, limit).stream()
                .map(TemplateSuggestionResponse::of)
                .toList());
    }

    @PostMapping("/shape-check")
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_CREATE')")
    @Operation(
            summary = "Does this look like a process rather than a task?",
            description = "TASKLIB-CONVERT-TO-PROCESS-01, the suggesting half. Saves nothing and decides "
                    + "nothing: it returns what the product suspects, each hint quoting the thing that "
                    + "triggered it, so a person can settle it by looking. DECISION-TASK-PARTS-01 draws "
                    + "the line — a checklist item has no state, no assignee, no deadline and appears in "
                    + "nobody's queue, where a process step has all four — and only a person can say "
                    + "which side a particular piece of work falls on. Ordinary work returns an empty "
                    + "list, which is the common case and stays silent.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Every suspicion, or none"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content)
    })
    public ResponseEntity<List<ProcessShapeHint>> shapeCheck(@RequestBody ShapeCheckRequest request) {
        return ResponseEntity.ok(templates.inspectShape(new TemplateDetails(
                request.title(),
                request.description(),
                request.type(),
                request.priority(),
                request.estimatedHours(),
                request.checklist())));
    }

    private TaskTemplateResponse described(TaskTemplate template) {
        return TaskTemplateResponse.of(template, templates.inspectShape(template.details()));
    }

    private TemplateDetails detailsOf(TemplateDraftRequest request) {
        return new TemplateDetails(
                request.title(),
                request.description(),
                request.type(),
                request.priority(),
                request.estimatedHours(),
                request.checklist());
    }

    @ExceptionHandler(TemplateNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(TemplateNotFoundException failure) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("TEMPLATE_NOT_FOUND", failure.getMessage()));
    }

    @ExceptionHandler(IllegalTemplateTransitionException.class)
    ResponseEntity<ErrorResponse> refusedByItsState(IllegalTemplateTransitionException failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("TEMPLATE_STATE_REFUSES", failure.getMessage()));
    }

    @ExceptionHandler(NotTheAuthorException.class)
    ResponseEntity<ErrorResponse> notTheAuthor(NotTheAuthorException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("NOT_PERMITTED", failure.getMessage()));
    }

    @ExceptionHandler(UnknownBandException.class)
    ResponseEntity<ErrorResponse> unknownBand(UnknownBandException failure) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        "UNKNOWN_BAND",
                        "There is no band called " + failure.band()
                                + ". Use one of: not-started, running, blocked, in-review, finished, overdue."));
    }

    @ExceptionHandler(UnknownMetadataValueException.class)
    ResponseEntity<ErrorResponse> unknownMetadataValue(UnknownMetadataValueException failure) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("UNKNOWN_METADATA_VALUE", failure.getMessage()));
    }
}
