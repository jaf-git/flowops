package com.flowops.nodepipeline.api;

import com.flowops.nodepipeline.application.ComposeDiscoveredProcesses;
import com.flowops.shared.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/node-pipeline/compositions")
@Tag(
        name = "Node pipeline compositions",
        description = "Turning discovered work into artefacts a person can approve. Everything written here is a "
                + "DRAFT, and the order it observed blocks nothing until somebody confirms it.")
public class PipelineCompositionController {
    private final ComposeDiscoveredProcesses composition;

    public PipelineCompositionController(ComposeDiscoveredProcesses composition) {
        this.composition = composition;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROCESS_TEMPLATE_AUTHOR') and hasAuthority('TASK_TEMPLATE_CREATE')")
    @Operation(
            summary = "Write down the processes the pipeline discovered",
            description = "NODEPIPE-COMPOSE-01. Runs C1–C4 over the last 30 days of marked work: resolves each "
                    + "discovered kind of work to a task template that already means it, drafts one where nothing "
                    + "does, composes a process per discovered shape, and records the order it observed as "
                    + "OBSERVED edges (ADR-004) which draw on screen and block nothing until a person promotes "
                    + "one. Everything written is a DRAFT — `blocksApproval` says what would stop each being "
                    + "approved as it stands, and on a first run that is normally its own steps still being "
                    + "drafts, which is ADR-015 working rather than something failing. Requires "
                    + "PROCESS_TEMPLATE_AUTHOR and TASK_TEMPLATE_CREATE, because it writes into both libraries.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "What was written down, and what was already there"),
        @ApiResponse(responseCode = "401", description = "No session.", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold both permissions",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ComposeDiscoveredProcesses.Composed> compose() {
        return ResponseEntity.ok(composition.compose());
    }
}
