package com.flowops.discovery.api;

import com.flowops.discovery.api.dto.ComposeProcessRequest;
import com.flowops.discovery.api.dto.ComposedResponse;
import com.flowops.discovery.api.dto.FormaliseNodeRequest;
import com.flowops.discovery.api.dto.FormalisedResponse;
import com.flowops.discovery.api.dto.WriteItDownRequest;
import com.flowops.discovery.api.dto.WrittenDownResponse;
import com.flowops.discovery.application.crossing.ComposeProcessUseCase;
import com.flowops.discovery.application.crossing.FormaliseWorkUseCase;
import com.flowops.discovery.application.crossing.WriteItDownUseCase;
import com.flowops.shared.web.ErrorResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discovery")
@Tag(
        name = "Discovery",
        description = "The work a business does, discovered from what its people said rather than described.")
public class FormalisationController {
    private final FormaliseWorkUseCase formaliseWork;
    private final ComposeProcessUseCase composeProcess;
    private final WriteItDownUseCase writeItDown;

    public FormalisationController(
            FormaliseWorkUseCase formaliseWork, ComposeProcessUseCase composeProcess, WriteItDownUseCase writeItDown) {
        this.formaliseWork = formaliseWork;
        this.composeProcess = composeProcess;
        this.writeItDown = writeItDown;
    }

    @Operation(
            summary = "Make this observed work a task template",
            description =
                    """
                    DISCOVERY-FORMALISE-WORK-01. Requires WORK_NODE_MARK, plus TASKLIB's own
                    TASK_TEMPLATE_CREATE and TASK_TEMPLATE_APPROVE — called rather than copied, and
                    re-checked by TASKLIB on its own side. No permission in DISCOVERY creates a
                    template, which is DECISION-DISCOVERY-ZONE-01's crossing rule expressed as
                    permissions: the one thing that crosses does so under the receiving zone's
                    authority.

                    THIS IS THE CROSSING POINT. Everything below it is observation — thin, unverified,
                    cheap to be wrong about. Everything above it is governed. The node is not consumed
                    and does not disappear: it keeps being the observation it always was and gains a
                    pointer to the verified unit somebody made from it.

                    A NODE BECOMES A TEMPLATE AND NEVER A TASK (invariant I7). There is no auto-create
                    path in this feature and no confidence high enough to justify one. A template that
                    appeared without a person floods a shared vocabulary with words nobody chose, which
                    is the failure this design is built against. The form is pre-filled so agreeing
                    costs almost nothing; agreeing is still what happens.

                    The steps become the template's checklist (DISCOVERY_03 section 3). They are
                    optional: a node nobody broke down is ordinary, and inventing steps would be the
                    product writing somebody's checklist for them.

                    A second attempt is refused rather than repointed. Two templates from one
                    observation is two vocabulary entries for one activity, and repointing the node to
                    the second would silently disown the first — which processes may already reference.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The library entry this work became"),
        @ApiResponse(responseCode = "400", description = "A blank title, or a blank step", content = @Content),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller lacks WORK_NODE_MARK, or TASKLIB's own create and approve",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "NODE_ALREADY_TEMPLATED: this observation is already in the library",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_WORK_NODE: no such unit of work — refused rather than conjured",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/nodes/{nodeId}/formalise")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<FormalisedResponse> formalise(
            @PathVariable UUID nodeId, @Valid @RequestBody FormaliseNodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FormalisedResponse.of(formaliseWork.execute(new FormaliseWorkUseCase.Formalise(
                        nodeId,
                        request.title(),
                        request.detail(),
                        request.steps() == null ? List.of() : request.steps()))));
    }

    @Operation(
            summary = "Compose a process template from this thread",
            description =
                    """
                    DISCOVERY-COMPOSE-PROCESS-01. Requires WORK_NODE_MARK plus PROCESS's own
                    PROCESS_TEMPLATE_AUTHOR, which is what ProcessTemplateController demands to author a
                    template by hand. Mirrored rather than chosen: a caller who may not write a process
                    template on that screen may not write one through this door either.

                    THE VOCABULARY MUST EXIST BEFORE THE GRAMMAR. A process template contains only task
                    templates (invariant I6), so a thread holding one unit of work nobody has formalised
                    has no step to offer for it and is refused with TRACK_NOT_FULLY_TEMPLATED. The
                    alternative is a placeholder step to be filled in later, which is how an SOP ships
                    with a hole in it — and a hole in an SOP reads to whoever follows it as a step
                    nobody bothered to write down.

                    The steps are the thread's own units of work, oldest first, and the request cannot
                    name them. A caller that could name the steps could name a step the thread does not
                    contain, and the process a person got would be a different process from the one they
                    were looking at.

                    NO RUN IS STARTED. A run is the application's case identifier and a track is this
                    zone's; the two exist precisely because there is no run here to supply one. What is
                    written is a template describing how the work is done — deciding to do it belongs to
                    somebody in the application, on PROCESS's own screen.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The process template this thread became"),
        @ApiResponse(responseCode = "400", description = "A blank name", content = @Content),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller lacks WORK_NODE_MARK or PROCESS_TEMPLATE_AUTHOR",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "TRACK_NOT_FULLY_TEMPLATED: some work in the thread is not a task template yet",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_TRACK: no such thread — threads are inferred from work, never conjured",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/tracks/{trackId}/compose")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK') and hasAuthority('PROCESS_TEMPLATE_AUTHOR')")
    public ResponseEntity<ComposedResponse> compose(
            @PathVariable UUID trackId, @Valid @RequestBody ComposeProcessRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ComposedResponse.of(
                        composeProcess.execute(new ComposeProcessUseCase.Compose(trackId, request.name()))));
    }

    @Operation(
            summary = "Write a recurring shape down as a process",
            description =
                    """
                    DISCOVERY-WRITE-IT-DOWN-01, and the product's thesis closed. A business does the
                    same thing over and over and never writes it down; everybody knows roughly how a job
                    goes, nobody has the sequence, and whoever joins next month learns it by being told
                    the wrong version twice. The pipeline found the sequence from work that already
                    happened, without anybody describing anything. Until this door existed it could only
                    say so.

                    ONLY A PERSON OPENS IT. No schedule calls this and no confidence is high enough to;
                    the recommendation is a sentence with a button, and R12.3 means the product may
                    notice a process and may never decide the business has one. A process template that
                    appeared on its own is an account of how the work goes that nobody agreed to,
                    sitting in a library people are meant to follow.

                    THE VOCABULARY BEFORE THE GRAMMAR (invariant I6). Each work type in the shape is
                    resolved to a task template before the process is authored - resolved, not created,
                    so a workspace that writes down three processes containing design work has one
                    "Design" in its library rather than three (CONSTRAINT-TEMPLATE-FIRST-01).

                    THE NAME IS THE CALLER'S AND THE STEPS ARE NOT. The steps are the shape's own work
                    types in the order the graph recorded them; a request that could name them could
                    name a step the evidence does not contain. The name is required and never invented
                    here, because it enters a shared library everybody afterwards picks from.

                    NOTHING IS CONSUMED. The finding stays a finding and the shape stays a shape, both
                    still resting on the brackets they were drawn from. The proposal gains who decided
                    and what they produced.

                    Requires DISCOVERY_CANVAS_VIEW - which is what reading the pipeline takes - plus
                    PROCESS's own PROCESS_TEMPLATE_AUTHOR, mirrored rather than chosen: somebody who may
                    not write a process template on that screen may not write one through this door
                    either. TASKLIB re-checks its own pair on its own side of the wall.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The process template this shape became"),
        @ApiResponse(responseCode = "400", description = "A blank name", content = @Content),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller lacks DISCOVERY_CANVAS_VIEW or PROCESS_TEMPLATE_AUTHOR",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "RECOMMENDATION_ALREADY_DECIDED: somebody has already written this down or put it aside",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_RECOMMENDATION, or PROPOSAL_IS_NOT_A_SHAPE: nothing here to author",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/analysis/recommendations/{recommendationId}/write-it-down")
    @PreAuthorize("hasAuthority('DISCOVERY_CANVAS_VIEW') and hasAuthority('PROCESS_TEMPLATE_AUTHOR')")
    public ResponseEntity<WrittenDownResponse> writeItDown(
            @PathVariable UUID recommendationId, @Valid @RequestBody WriteItDownRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WrittenDownResponse.of(
                        writeItDown.execute(new WriteItDownUseCase.WriteItDown(recommendationId, request.name()))));
    }
}
