package com.flowops.nodepipeline.api;

import com.flowops.nodepipeline.application.MeasureJobElapsed;
import com.flowops.nodepipeline.domain.wait.JobElapsed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/node-pipeline/jobs")
@Tag(
        name = "Node pipeline waits",
        description = "How long an engagement took, split into the days the business spent and the days it "
                + "spent waiting on somebody outside it. The second number is not a performance figure.")
public class JobClockController {
    private final MeasureJobElapsed elapsed;

    public JobClockController(MeasureJobElapsed elapsed) {
        this.elapsed = elapsed;
    }

    @GetMapping("/{jobId}/elapsed")
    @PreAuthorize("hasAuthority('PIPELINE_RUN_VIEW')")
    @Operation(
            summary = "How long an engagement took, and how much of it was waiting on somebody else",
            description = "NODEPIPE-ELAPSED-01. Returns the engagement's total days, the days spent waiting on "
                    + "a client or a supplier, and the difference — which is the only one of the three that "
                    + "may enter a performance figure (R7.5). Overlapping waits are counted once, every span "
                    + "is clamped to the engagement (R7.10), and a wait is satisfied only by a DELIVERED or a "
                    + "DONE (R7.6): a handover, a drop, a lapse or a cadence close leaves it running. Requires "
                    + "PIPELINE_RUN_VIEW.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The split. No waits at all is an ordinary answer."),
        @ApiResponse(responseCode = "404", description = "There is no such engagement."),
        @ApiResponse(responseCode = "401", description = "No session."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold PIPELINE_RUN_VIEW.")
    })
    public ResponseEntity<JobElapsed> elapsed(@PathVariable UUID jobId) {
        return elapsed.of(jobId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound()
                .build());
    }
}
