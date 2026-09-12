package com.flowops.nodepipeline.api;

import com.flowops.nodepipeline.api.dto.ResemblanceRequest;
import com.flowops.nodepipeline.api.dto.ResemblanceResponse;
import com.flowops.nodepipeline.application.resemble.ResembleApprovedWorkUseCase;
import com.flowops.shared.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Node pipeline")
@RestController
@RequestMapping("/api/node-pipeline")
public class PipelineResemblanceController {
    private final ResembleApprovedWorkUseCase resemblance;

    public PipelineResemblanceController(ResembleApprovedWorkUseCase resemblance) {
        this.resemblance = resemblance;
    }

    @Operation(
            summary = "Does this look like an approved task template?",
            description =
                    """
                    NODEPIPE-RESEMBLE-WORK-01. Scores one sentence against the **approved** library with the
                    same matcher, the same weights and the same gates the batch run uses, and answers with
                    the one template it resembles — or with nothing.

                    **`match` is null far more often than not, and that is correct.** Most of what people
                    say is not work, and most work resembles nothing written down yet. On the measured
                    corpus 167 of 800 marks abstain. A caller that treats an absent match as a failure has
                    misread this endpoint: silence is its ordinary answer.

                    **Drafts are never recommended.** A draft is a proposal nobody agreed to, and the
                    library this reads is approved-only.

                    **It writes nothing** — no run, no decision, no node. Pressing it a hundred times
                    changes nothing about the workspace.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The template it resembles, or `match: null`"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_TEMPLATE_VIEW",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "REQUEST_INVALID: no text was sent",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/resemblance")
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_VIEW')")
    public ResponseEntity<ResemblanceResponse> resembling(@Valid @RequestBody ResemblanceRequest request) {
        return ResponseEntity.ok(ResemblanceResponse.of(
                resemblance.forWhatWasSaid(request.text()).orElse(null)));
    }
}
