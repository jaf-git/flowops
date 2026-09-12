package com.flowops.discovery.api;

import com.flowops.discovery.api.dto.CanvasResponse;
import com.flowops.discovery.application.canvas.ViewCanvasUseCase;
import com.flowops.shared.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discovery")
@Tag(
        name = "Discovery",
        description = "The work a business does, discovered from what its people said rather than described.")
public class CanvasController {
    private final ViewCanvasUseCase canvas;

    public CanvasController(ViewCanvasUseCase canvas) {
        this.canvas = canvas;
    }

    @Operation(
            summary = "The work canvas — lanes of threads, cards of work",
            description =
                    """
                    DISCOVERY-VIEW-CANVAS-01. Requires DISCOVERY_CANVAS_VIEW, which the owner and a
                    manager hold and an employee does not.

                    WHY THIS ONE IS THE MANAGER'S AND NOT THE EMPLOYEE'S, when marking work is the one
                    permission in this product deliberately granted to everybody. The rule is
                    DISCOVERY_02 §3: ask an employee only what they can answer by RECOGNITION, and ask a
                    manager anything requiring CLASSIFICATION. Every question this zone puts to an
                    employee is recognition — which engagement is this, what did the work produce, who
                    are you waiting on — and each is answered in a second because the person already
                    knows. This screen asks nothing and answers something else entirely: what shape does
                    our work have, which threads run in parallel, where does the time go. That is a
                    reading of the business rather than of a task, and it is the manager's job.

                    It is also the screen where the design's largest risk would land. R1 says adoption
                    collapses in week two if people stop clicking, and nothing would stop them faster
                    than a canvas of their own work that they could read as being read about them. The
                    permission is not seniority; it is what keeps the circle a tool rather than a
                    monitor.

                    THREE REFUSALS ARE STRUCTURAL HERE AND NONE OF THEM IS A FILTER.

                    The response carries NO person identifier and NO person name, anywhere — not on a
                    lane, not on a card. Lanes are ROLE PAIRS: `Account manager ↔ Content writer`, never
                    `Maria ↔ Andrei`. A lane labelled with a name is a performance dashboard wearing a
                    different hat and breaks invariant I5 by construction. The refusal lives in the SQL,
                    which selects no such column, rather than in a mapping that omits one — a figure a
                    permission hides has already been computed.

                    `phases` is a LIST OF ROWS and there is NO TOTAL FIELD — invariant I9, a duration is
                    never presented without its phase. Work and external waiting stay separate because
                    the client's silence must never enter a performance figure, and no arithmetic can
                    take a wait back out of a sum it was added to.

                    NO CONNECTOR CROSSES A LANE AND NO LANE CROSSES A JOB — invariants I1 and I2,
                    enforced in the query, so the canvas has nothing to draw rather than something it
                    declines to draw. A wall a confidence score can cross is not a wall.

                    A repeated cycle comes back as ONE entry in `loops` with a counter — `[review ⇄
                    revision] ×3` — never as six cards. Six cards says the work had six steps; it had one
                    thing three times.

                    An engagement with no work yet answers 200 with no lanes. An engagement that does not
                    exist answers 422, because an empty canvas is an ordinary state and a person meeting
                    one after a mistyped identifier would go looking for their work rather than for their
                    typo.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The engagement's threads, drawn as lanes"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold DISCOVERY_CANVAS_VIEW",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_JOB: no such engagement — refused rather than answered with an empty canvas",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/canvas")
    @PreAuthorize("hasAuthority('DISCOVERY_CANVAS_VIEW')")
    public ResponseEntity<CanvasResponse> canvas(@RequestParam UUID jobId) {
        return ResponseEntity.ok(CanvasResponse.of(canvas.of(jobId)));
    }
}
