package com.flowops.nodepipeline.api;

import com.flowops.nodepipeline.application.port.ProcessRunPort;
import com.flowops.shared.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/node-pipeline/processes")
@Tag(
        name = "Node pipeline processes",
        description = "Turning what the pipeline found into work that is actually running. Every artefact this "
                + "system proposes is a DRAFT somebody approves; starting a run is that approval being acted on.")
public class PipelineProcessController {
    private final ProcessRunPort processes;

    public PipelineProcessController(ProcessRunPort processes) {
        this.processes = processes;
    }

    @GetMapping("/startable")
    @PreAuthorize("hasAuthority('PROCESS_INSTANTIATE')")
    @Operation(
            summary = "Which processes a run could be started from",
            description = "NODEPIPE-START-PROCESS-01, the picker. The active process library, answered by "
                    + "PROCESS through its published surface rather than read here — a second read of "
                    + "somebody else's library is a second answer that has only to drift once. Requires "
                    + "PROCESS_INSTANTIATE, the same authority the start itself needs, so the list never "
                    + "offers a door the next click refuses.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The startable processes. Empty is a real answer."),
        @ApiResponse(responseCode = "401", description = "No session.", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold PROCESS_INSTANTIATE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<ProcessRunPort.StartableProcess>> startable() {
        return ResponseEntity.ok(processes.startable());
    }

    @PostMapping("/{templateId}/runs")
    @PreAuthorize("hasAuthority('PROCESS_INSTANTIATE')")
    @Operation(
            summary = "Run this process",
            description = "NODEPIPE-START-PROCESS-01. Starts a run of a process from the pipeline's own screens, "
                    + "through PROCESS-INSTANTIATE-01 and nothing else: the template is copied into the run "
                    + "(DECISION-PROCESS-SNAPSHOT-01), its graph is re-validated because it may have been "
                    + "edited since it was authored, every entry step becomes reachable at once, and the whole "
                    + "of that commits together. **The run's steps reflect the confirmed edges** — the "
                    + "acceptance of 09_PHASES.md P16. An absent name takes the process's own; the pipeline "
                    + "has no name of its own to lend a run. Requires PROCESS_INSTANTIATE, which is "
                    + "non-delegable.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The run has started; its identifier is returned."),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID: no Process Owner was named",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session.", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold PROCESS_INSTANTIATE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "TEMPLATE_NOT_FOUND: there is no such process",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "GRAPH_CYCLE or STEP_STRANDED when the process was edited into an invalid graph "
                        + "since it was authored, and no run is created; TEMPLATE_IS_RETIRED when it has left "
                        + "the library; ASSIGNEE_NOT_ACTIVE when the person named can no longer steer a run",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<StartedRunResponse> start(
            @PathVariable UUID templateId, @Valid @RequestBody StartRunRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new StartedRunResponse(processes.startRun(templateId, request.name(), request.processOwnerId())));
    }

    public record StartRunRequest(String name, @NotNull UUID processOwnerId) {}

    public record StartedRunResponse(UUID instanceId) {}
}
