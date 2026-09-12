package com.flowops.process.api;

import com.flowops.process.api.dto.AuthorTemplateRequest;
import com.flowops.process.api.dto.DependencyRequest;
import com.flowops.process.api.dto.EditTemplateRequest;
import com.flowops.process.api.dto.ProcessMetadataRequest;
import com.flowops.process.api.dto.TemplateListResponse;
import com.flowops.process.api.dto.TemplateResponse;
import com.flowops.process.api.dto.TemplateUsesResponse;
import com.flowops.process.api.mapper.ProcessDtoMapper;
import com.flowops.process.application.authortemplate.AuthorTemplateCommand;
import com.flowops.process.application.authortemplate.AuthorTemplateUseCase;
import com.flowops.process.application.definedependency.DefineDependencyCommand;
import com.flowops.process.application.definedependency.DefineDependencyUseCase;
import com.flowops.process.application.definedependency.PromoteDependencyUseCase;
import com.flowops.process.application.definedependency.RemoveDependencyUseCase;
import com.flowops.process.application.edittemplate.EditTemplateCommand;
import com.flowops.process.application.edittemplate.EditTemplateUseCase;
import com.flowops.process.application.retiretemplate.RetireTemplateUseCase;
import com.flowops.process.application.templatemetadata.ProcessMetadataUseCase;
import com.flowops.process.application.viewtemplates.ViewTemplateUsesUseCase;
import com.flowops.process.application.viewtemplates.ViewTemplatesUseCase;
import com.flowops.process.domain.model.ProcessMetadata;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TemplateId;
import com.flowops.shared.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/process-templates")
@Tag(name = "Process templates", description = "How a recurring piece of work is defined, before anybody runs it.")
public class ProcessTemplateController {
    private final AuthorTemplateUseCase authorTemplateUseCase;
    private final DefineDependencyUseCase defineDependencyUseCase;
    private final PromoteDependencyUseCase promoteDependencyUseCase;
    private final RemoveDependencyUseCase removeDependencyUseCase;
    private final EditTemplateUseCase editTemplateUseCase;
    private final ViewTemplatesUseCase viewTemplatesUseCase;
    private final ViewTemplateUsesUseCase viewTemplateUsesUseCase;
    private final RetireTemplateUseCase retireTemplateUseCase;
    private final ProcessMetadataUseCase processMetadataUseCase;
    private final ProcessDtoMapper mapper;

    public ProcessTemplateController(
            RetireTemplateUseCase retireTemplateUseCase,
            ProcessMetadataUseCase processMetadataUseCase,
            AuthorTemplateUseCase authorTemplateUseCase,
            DefineDependencyUseCase defineDependencyUseCase,
            PromoteDependencyUseCase promoteDependencyUseCase,
            RemoveDependencyUseCase removeDependencyUseCase,
            EditTemplateUseCase editTemplateUseCase,
            ViewTemplatesUseCase viewTemplatesUseCase,
            ViewTemplateUsesUseCase viewTemplateUsesUseCase,
            ProcessDtoMapper mapper) {
        this.retireTemplateUseCase = retireTemplateUseCase;
        this.processMetadataUseCase = processMetadataUseCase;
        this.authorTemplateUseCase = authorTemplateUseCase;
        this.defineDependencyUseCase = defineDependencyUseCase;
        this.promoteDependencyUseCase = promoteDependencyUseCase;
        this.removeDependencyUseCase = removeDependencyUseCase;
        this.editTemplateUseCase = editTemplateUseCase;
        this.viewTemplatesUseCase = viewTemplatesUseCase;
        this.viewTemplateUsesUseCase = viewTemplateUsesUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROCESS_TEMPLATE_AUTHOR')")
    @Operation(
            summary = "Record how the business does a recurring thing",
            description = "PROCESS-AUTHOR-TEMPLATE-01. Requires PROCESS_TEMPLATE_AUTHOR at this endpoint. "
                    + "The template is born with steps and no dependencies, which is a valid graph where every "
                    + "step is an entry step; edges are drawn afterwards through "
                    + "PROCESS-DEFINE-DEPENDENCIES-01.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The template, with an identifier for every step"),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID when the name is blank or no step is given; STEP_TITLE_REQUIRED "
                        + "when a step has no title, naming its position in the details",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold PROCESS_TEMPLATE_AUTHOR",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "TEMPLATE_NAME_TAKEN: an active template already carries this name",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TemplateResponse> author(@Valid @RequestBody AuthorTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toResponse(authorTemplateUseCase.execute(new AuthorTemplateCommand(
                        request.name(), request.overview(), mapper.toDrafts(request.steps())))));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROCESS_TEMPLATE_AUTHOR') or hasAuthority('PROCESS_INSTANTIATE')")
    @Operation(
            summary = "The templates somebody may run",
            description = "The library read. No use case names it and three need it — an editor loads what it "
                    + "edits, an instantiator chooses what to run, an author sees what they authored. Decision "
                    + "row 205.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Every active template, newest first"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller may neither author nor instantiate",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TemplateListResponse> library() {
        return ResponseEntity.ok(mapper.toList(viewTemplatesUseCase.all()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PROCESS_TEMPLATE_AUTHOR') or hasAuthority('PROCESS_INSTANTIATE')")
    @Operation(summary = "One template with its whole graph", description = "Decision row 205.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The template, its steps and its edges"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TEMPLATE_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TemplateResponse> one(@PathVariable UUID id) {
        return ResponseEntity.ok(mapper.toResponse(viewTemplatesUseCase.one(TemplateId.of(id))));
    }

    @GetMapping("/using/{taskTemplateId}")
    @PreAuthorize("hasAuthority('PROCESS_TEMPLATE_AUTHOR') or hasAuthority('PROCESS_INSTANTIATE')")
    @Operation(
            summary = "Where one piece of work is used",
            description = "PROCESS's half of a task template's lineage: which process templates plan this "
                    + "work, and which runs have cut a step from it. TASKLIB answers the other half — what "
                    + "the template says and how its tasks have gone — and the page composes the two.\n\n"
                    + "It lives here rather than in TASKLIB because both tables are PROCESS's, and because "
                    + "TASKLIB asking PROCESS is the dependency removed on 2026-08-22; restoring it would "
                    + "close a cycle between the two features.\n\n"
                    + "**A template nobody uses and a template that does not exist answer alike** — two "
                    + "empty lists. Distinguishing them would make this a way to ask which identifiers "
                    + "exist. **No figure here is keyed to a person**: a run carries a name and a state, "
                    + "never who holds its work.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The templates that plan it and the runs that cut it"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TemplateUsesResponse> using(@PathVariable UUID taskTemplateId) {
        return ResponseEntity.ok(TemplateUsesResponse.of(viewTemplateUsesUseCase.of(taskTemplateId)));
    }

    @PatchMapping("/{id}/metadata")
    @PreAuthorize("hasAuthority('PROCESS_TEMPLATE_EDIT')")
    @Operation(
            summary = "Record what this template says about its runs",
            description = "SOP-METADATA-01. What starts a run, how you know it has finished, and who normally "
                    + "owns one — a role, never a person, because a procedure naming Andrei stops being true the "
                    + "day Andrei leaves (SOP_02 section 10). "
                    + "**All three at once, which is the opposite of the task-template ask and deliberately so**: a "
                    + "task template is stamped constantly, so its metadata is drip-fed one field per use and a form "
                    + "there is friction people route around; a process template is authored once and edited rarely, "
                    + "by somebody already looking at the whole shape. "
                    + "Nulls clear. Every field is nullable forever, and a template with none of them runs exactly as "
                    + "it always has. "
                    + "**There is no duration field** — SOP_01 section 2 lists one and SOP_02 section 5 already "
                    + "specifies the same figure as a median over completed runs, so a column would be a second "
                    + "source going stale on a document somebody follows (SOP_REQ_SPEC_01).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recorded, and the template as it now reads"),
        @ApiResponse(responseCode = "400", description = "The owner role is not one of the three", content = @Content),
        @ApiResponse(responseCode = "404", description = "No such template", content = @Content)
    })
    public ResponseEntity<TemplateResponse> describe(
            @PathVariable UUID id, @Valid @RequestBody ProcessMetadataRequest request) {
        return ResponseEntity.ok(mapper.toResponse(processMetadataUseCase.describe(
                TemplateId.of(id),
                new ProcessMetadata(request.triggerNote(), request.endCondition(), request.ownerRole()))));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PROCESS_TEMPLATE_EDIT')")
    @Operation(
            summary = "Refine a template",
            description = "PROCESS-EDIT-TEMPLATE-01. Requires PROCESS_TEMPLATE_EDIT at this endpoint, and a "
                    + "manager may change only a template they authored — which is checked in the use case "
                    + "because it depends on the request. A step submitted without an identifier is added; a "
                    + "step of this template absent from the list is removed, and its edges go with it in the "
                    + "same transaction. **No running instance is affected** "
                    + "(DECISION-PROCESS-SNAPSHOT-01).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The template as it now stands"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED, or NOT_THE_AUTHOR when a manager edits somebody else's template",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TEMPLATE_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "GRAPH_CYCLE or GRAPH_STEP_STRANDED: the edit would leave an invalid graph",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "TEMPLATE_NEEDS_A_STEP, STEP_TITLE_REQUIRED or UNKNOWN_STEP",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TemplateResponse> edit(
            @PathVariable UUID id, @Valid @RequestBody EditTemplateRequest request) {
        return ResponseEntity.ok(mapper.toResponse(editTemplateUseCase.execute(
                new EditTemplateCommand(TemplateId.of(id), request.overview(), mapper.toDrafts(request.steps())))));
    }

    @PostMapping("/{id}/retirement")
    @PreAuthorize("hasAuthority('PROCESS_TEMPLATE_RETIRE')")
    @Operation(
            summary = "Take a template out of the library",
            description = "PROCESS-RETIRE-TEMPLATE-01. Requires PROCESS_TEMPLATE_RETIRE at this endpoint, and "
                    + "a manager may retire only a template they authored — checked in the use case because it "
                    + "depends on the row. **Nothing is deleted.** Every run already cut from it keeps working "
                    + "and its provenance still resolves, because a run is a snapshot that never reads the "
                    + "template it came from. What changes is that it leaves the library list and can no longer "
                    + "be started: `POST /api/process-instances` answers TEMPLATE_IS_RETIRED. Retiring also "
                    + "frees the template's name, since the unique index on it is partial — a process written "
                    + "badly and retired must not stop the same process being written properly.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The template, now retired"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED, or NOT_THE_AUTHOR when a manager retires somebody else's template",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TEMPLATE_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "TEMPLATE_IS_RETIRED — it already is, so nothing would change and nothing is logged",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TemplateResponse> retire(@PathVariable UUID id) {
        return ResponseEntity.ok(mapper.toResponse(retireTemplateUseCase.execute(TemplateId.of(id))));
    }

    @PostMapping("/{id}/dependencies")
    @PreAuthorize("hasAuthority('PROCESS_TEMPLATE_AUTHOR')")
    @Operation(
            summary = "Say that one step waits for another",
            description = "PROCESS-DEFINE-DEPENDENCIES-01. Requires PROCESS_TEMPLATE_AUTHOR at this endpoint; "
                    + "the graph invariants are enforced additionally in the domain, because the permission asks "
                    + "whether this person may edit a graph and the invariant asks whether the graph is still "
                    + "valid. Drawing the same edge twice succeeds, creates no duplicate and appends no second "
                    + "event.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The template with its updated graph"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TEMPLATE_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "GRAPH_CYCLE, naming every step of the cycle in the details",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "CROSS_TEMPLATE_EDGE when the edge reaches into another template; UNKNOWN_STEP "
                        + "when it names a step that exists nowhere",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TemplateResponse> draw(@PathVariable UUID id, @Valid @RequestBody DependencyRequest request) {
        return ResponseEntity.ok(mapper.toResponse(defineDependencyUseCase.execute(new DefineDependencyCommand(
                TemplateId.of(id), StepId.of(request.dependentStepId()), StepId.of(request.dependsOnStepId())))));
    }

    @PostMapping("/{id}/dependencies/promote")
    @PreAuthorize("hasAuthority('PROCESS_TEMPLATE_AUTHOR')")
    @Operation(
            summary = "Decide that an observed order must hold",
            description = "PROCESS-DEFINE-DEPENDENCIES-01, ADR-004. The pipeline records the order it watched "
                    + "happen as OBSERVED edges, which draw on screen and block nothing; this is the act that "
                    + "turns one of them into a constraint, after which it participates in cycle checking and "
                    + "reachability like any hand-drawn edge. Requires PROCESS_TEMPLATE_AUTHOR. Promoting an "
                    + "edge that is already confirmed, or one that was never observed, succeeds and changes "
                    + "nothing — the card a person pressed twice must not fail the second time.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The template with its updated graph"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TEMPLATE_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "GRAPH_CYCLE, naming every step of the cycle. The observation is left as it was: "
                        + "it happened, and what is refused is the claim that it must always happen",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "CROSS_TEMPLATE_EDGE or UNKNOWN_STEP",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TemplateResponse> promote(
            @PathVariable UUID id, @Valid @RequestBody DependencyRequest request) {
        return ResponseEntity.ok(mapper.toResponse(promoteDependencyUseCase.execute(new DefineDependencyCommand(
                TemplateId.of(id), StepId.of(request.dependentStepId()), StepId.of(request.dependsOnStepId())))));
    }

    @DeleteMapping("/{id}/dependencies/{dependentStepId}/{dependsOnStepId}")
    @PreAuthorize("hasAuthority('PROCESS_TEMPLATE_AUTHOR')")
    @Operation(
            summary = "Take a dependency back out",
            description = "PROCESS-DEFINE-DEPENDENCIES-01. The removal is validated against the graph exactly as "
                    + "the addition is. Removing an edge that is not drawn succeeds and changes nothing, for the "
                    + "same reason drawing one twice does.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The template with its updated graph"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TEMPLATE_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "GRAPH_STEP_STRANDED: the removal would leave a step nothing reaches",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "CROSS_TEMPLATE_EDGE or UNKNOWN_STEP",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TemplateResponse> erase(
            @PathVariable UUID id, @PathVariable UUID dependentStepId, @PathVariable UUID dependsOnStepId) {
        return ResponseEntity.ok(mapper.toResponse(removeDependencyUseCase.execute(new DefineDependencyCommand(
                TemplateId.of(id), StepId.of(dependentStepId), StepId.of(dependsOnStepId)))));
    }
}
