package com.flowops.aiassist.api;

import com.flowops.aiassist.api.dto.ShapeSuggestionResponse;
import com.flowops.aiassist.application.SuggestShapeUseCase;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-assist")
@Tag(name = "AI assist", description = "What a model makes of work the deterministic rules do not catch")
public class ShapeSuggestionController {
    private final SuggestShapeUseCase suggestions;

    public ShapeSuggestionController(SuggestShapeUseCase suggestions) {
        this.suggestions = suggestions;
    }

    @GetMapping("/template-shape/{templateId}")
    @PreAuthorize("hasAuthority('TASK_TEMPLATE_VIEW')")
    @Operation(
            summary = "Does this template describe a process rather than a task?",
            description = "The case the deterministic rules miss. `ProcessShapeHeuristic` catches long "
                    + "checklists and handover language, and a test asserts that it returns **nothing** for "
                    + "*Onboarding client nou: strânge brieful, trimite contractul, programează kickoff-ul* "
                    + "— four handovers between three people. Decision row 374 records that miss rather than "
                    + "papering over it, and it is the one place in this product where the rules honestly "
                    + "run out.\n\n"
                    + "**The model never supplies a word that reaches a screen.** It answers with the *keys* "
                    + "of checklist items and an order; every step's text is read back out of the evidence. "
                    + "The first probe of this feature asked it to title the steps and it returned *Client "
                    + "brief* and *Contract signing* for a template written in Romanian — fluent, plausible, "
                    + "and nobody's words.\n\n"
                    + "**It never produces a number.** There is no numeric field on the response but a count "
                    + "of evidence lines, which the product computes, and a rationale containing a digit is "
                    + "refused outright rather than stripped.\n\n"
                    + "**Every step is traceable or dropped**, and if more than half drop the whole "
                    + "suggestion is suppressed — a suggestion assembled from the checkable third of an "
                    + "answer is a different answer, one nobody produced.\n\n"
                    + "Answers 200 in every case. `available: false` means no model is configured, which is "
                    + "a supported way to run the product; `suggested: false` means it was asked and saw no "
                    + "process, which is the ordinary answer. Cached for ten minutes, misses included.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The suggestion, or the fact that there is none"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold TASK_TEMPLATE_VIEW",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ShapeSuggestionResponse> templateShape(@PathVariable UUID templateId) {
        if (!suggestions.isAvailable()) {
            return ResponseEntity.ok(ShapeSuggestionResponse.unavailable());
        }
        return ResponseEntity.ok(suggestions
                .shapeOf(templateId)
                .map(ShapeSuggestionResponse::of)
                .orElseGet(ShapeSuggestionResponse::nothing));
    }
}
