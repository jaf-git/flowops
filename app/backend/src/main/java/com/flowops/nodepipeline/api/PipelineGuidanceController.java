package com.flowops.nodepipeline.api;

import com.flowops.nodepipeline.application.AiSwitch;
import com.flowops.nodepipeline.application.ExplainDiscovery;
import com.flowops.nodepipeline.application.port.PipelineGraphPort;
import com.flowops.nodepipeline.application.port.WorkJudgePort;
import com.flowops.nodepipeline.domain.ai.Judgement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/node-pipeline")
@Tag(
        name = "Pipeline guidance",
        description = "What the pipeline has found, and what a person would need to know to do it. "
                + "The guidance is written by a language model and says so.")
public class PipelineGuidanceController {
    private final ExplainDiscovery explain;
    private final AiSwitch aiSwitch;
    private final WorkJudgePort judge;

    public PipelineGuidanceController(ExplainDiscovery explain, AiSwitch aiSwitch, WorkJudgePort judge) {
        this.explain = explain;
        this.aiSwitch = aiSwitch;
        this.judge = judge;
    }

    @GetMapping("/discoveries")
    @PreAuthorize("hasAuthority('PIPELINE_RUN_VIEW')")
    @Operation(
            summary = "The templates and processes the pipeline drew out of the graph",
            description = "NODEPIPE-VIEW-DISCOVERIES-01. Reads and decides nothing. Drafts are included: "
                    + "what the pipeline has proposed is a different question from what has been "
                    + "approved, and this page asks the first one. Requires PIPELINE_RUN_VIEW.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Everything found. An empty list is an ordinary answer."),
        @ApiResponse(responseCode = "401", description = "No session."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold PIPELINE_RUN_VIEW.")
    })
    public List<ExplainDiscovery.Discovered> discoveries() {
        return explain.everythingFound();
    }

    @GetMapping("/adoption")
    @PreAuthorize("hasAuthority('PIPELINE_RUN_VIEW')")
    @Operation(
            summary = "How many marks name an activity",
            description = "NODEPIPE-VIEW-DISCOVERIES-01. The one figure that predicts step resolution better "
                    + "than any tuning: a step is keyed on the activity a person named, falling back to the "
                    + "kind of work when nobody named one. Where this sits low the shapes above are coarse "
                    + "for a reason no clustering can fix, and a reader needs to see that rather than "
                    + "conclude the discovery is poor. Bracket closures are excluded, because a closure "
                    + "inherits its bracket's activity rather than choosing one. Requires PIPELINE_RUN_VIEW.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "The count, its denominator and the share. Zero of zero "
                        + "on an empty graph is an ordinary answer."),
        @ApiResponse(responseCode = "401", description = "No session."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold PIPELINE_RUN_VIEW.")
    })
    public AdoptionResponse adoption() {
        PipelineGraphPort.Adoption adoption = explain.adoption();
        return new AdoptionResponse(adoption.marks(), adoption.naming(), adoption.share());
    }

    public record AdoptionResponse(long marks, long naming, double share) {}

    public record NearMissRequest(@NotBlank String typed, @NotNull List<String> candidates) {}

    public record NearMissResponse(String same, double confidence, String reason, String modelId) {}

    @PostMapping("/activities/near-miss")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @Operation(
            summary = "Ask a language model whether a typed activity already has a name",
            description =
                    """
                    DISCOVERY-MANAGE-ACTIVITIES-01, and the one place in the activity field where a
                    model belongs. The browser already catches "caption" against "captions" by
                    comparing characters, for nothing and instantly. It cannot catch "post copy"
                    against "caption set", which share none.

                    **It suggests and never merges.** The answer is rendered as the same question a
                    character match renders — this looks like X, use it or say yours is different —
                    and nothing is written either way. An automatic merge that is wrong makes two
                    different activities one, and that loss is silent and unrecoverable.

                    Answers 409 when the model is off, unavailable or this plug point is not
                    switched on, and 204 when it has no opinion. Those are different facts and a
                    caller that cannot tell them apart would show the person a wrong reason.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The existing activity the model considers the same work."),
        @ApiResponse(responseCode = "204", description = "The model has no opinion. An ordinary answer."),
        @ApiResponse(responseCode = "409", description = "AI_UNAVAILABLE: the model is off, or this plug point is."),
        @ApiResponse(responseCode = "401", description = "No session."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold WORK_NODE_MARK.")
    })
    public ResponseEntity<NearMissResponse> nearMiss(@Valid @RequestBody NearMissRequest asked) {
        if (!judge.isEnabled(Judgement.PlugPoint.SAME_ACTIVITY)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                    .build();
        }

        if (asked.candidates().isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return judge.judge(new Judgement.Question(
                        Judgement.PlugPoint.SAME_ACTIVITY, asked.typed(), asked.candidates(), null))
                .filter(verdict ->
                        asked.candidates().stream().anyMatch(candidate -> candidate.equalsIgnoreCase(verdict.value())))
                .map(verdict -> ResponseEntity.ok(
                        new NearMissResponse(verdict.value(), verdict.confidence(), verdict.reason(), judge.modelId())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/discoveries/{id}/guidance")
    @PreAuthorize("hasAuthority('PIPELINE_RUN_VIEW')")
    @Operation(
            summary = "Ask a language model to explain one of them",
            description =
                    """
                    NODEPIPE-EXPLAIN-DISCOVERY-01. Composes handover guidance from what the graph
                    observed about this work, for somebody doing it for the first time.

                    **This is the one plug point that composes rather than selects.** The other
                    three order words already present; this one writes sentences, so its answer
                    is a claim the product cannot substantiate from its own evidence and is
                    labelled as model-written wherever it appears.

                    It writes nothing: no template is changed, no guidance is stored, and asking
                    twice costs two answers rather than one record. Answers 409 when the model is
                    switched off or unavailable, because silence and "there is nothing to say"
                    are different facts.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The guidance, and the model that wrote it."),
        @ApiResponse(responseCode = "404", description = "Nothing found under that id."),
        @ApiResponse(responseCode = "409", description = "AI_UNAVAILABLE: the model is off or not configured."),
        @ApiResponse(responseCode = "401", description = "No session.")
    })
    public ResponseEntity<ExplainDiscovery.Guidance> guidance(@PathVariable String id) {
        if (!judge.isEnabled(Judgement.PlugPoint.EXPLAIN)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                    .build();
        }
        return explain.explain(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound()
                .build());
    }

    public record AiState(boolean available, boolean on, String modelId, String promptVersion) {}

    @GetMapping("/ai")
    @PreAuthorize("hasAuthority('PIPELINE_RUN_VIEW')")
    @Operation(
            summary = "Whether a model is configured, and whether it is switched on",
            description = "Two different facts. `available` says a model was configured when the server "
                    + "started and cannot be changed from here; `on` is the runtime switch. A model "
                    + "that is unavailable cannot be switched on.")
    public AiState state() {
        return new AiState(judge.isAvailable(), aiSwitch.isOn(), judge.modelId(), judge.promptVersion());
    }

    public record SetAi(@NotNull Boolean on) {}

    @PutMapping("/ai")
    @PreAuthorize("hasAuthority('PIPELINE_RUN_START')")
    @Operation(
            summary = "Switch the model on or off for every plug point at once",
            description = "Gates all plug points together. A half-on model is a configuration nobody "
                    + "can reason about afterwards, and the question people ask in a demonstration "
                    + "is binary: what does this look like without it? Requires PIPELINE_RUN_START, "
                    + "because it changes what a run does.")
    public AiState set(@RequestBody SetAi wanted) {
        aiSwitch.set(Boolean.TRUE.equals(wanted.on()));
        return state();
    }
}
