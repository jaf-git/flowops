package com.flowops.nodepipeline.api;

import com.flowops.nodepipeline.application.DeliverPipelineFindings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/node-pipeline/notices")
@Tag(
        name = "Node pipeline notices",
        description = "What the last run found, delivered to the people who can act on it — and nothing to "
                + "anybody who cannot. Most of what the pipeline decided is never sent.")
public class PipelineNoticesController {
    private final DeliverPipelineFindings delivery;

    public PipelineNoticesController(DeliverPipelineFindings delivery) {
        this.delivery = delivery;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PIPELINE_RUN_START')")
    @Operation(
            summary = "Deliver the last run's findings to the people who can act on them",
            description = "NODEPIPE-NOTIFY-01. Applies the delivery policy R1–R6 to the most recent run and "
                    + "raises one notice per surviving finding. A job spoken about at job level does not "
                    + "also nudge its people; a node is nudged once for all time; a repeated habit "
                    + "escalates once and is then silent; nobody receives more than three in one digest; "
                    + "a stalled engagement produces a stall notice and no nudges; and anything already "
                    + "shown in an earlier digest is not shown again. Abstentions notify nobody and "
                    + "nothing is created — every artefact this system proposes is a DRAFT somebody "
                    + "approves. Requires PIPELINE_RUN_START.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "The digest was composed. Sending nothing is a correct and common outcome."),
        @ApiResponse(
                responseCode = "404",
                description = "The pipeline has never run — not the same as finding nothing."),
        @ApiResponse(responseCode = "401", description = "No session."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold PIPELINE_RUN_START.")
    })
    public ResponseEntity<DeliverPipelineFindings.Delivery> deliver() {
        return delivery.deliverLatest().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound()
                .build());
    }
}
