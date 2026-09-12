package com.flowops.aiinsight.api;

import com.flowops.aiinsight.application.DecideOnInsightUseCase;
import com.flowops.aiinsight.application.ViewInsightsUseCase;
import com.flowops.aiinsight.application.ViewInsightsUseCase.Detail;
import com.flowops.aiinsight.application.exception.InsightNoLongerHoldsException;
import com.flowops.aiinsight.application.exception.InsightNotActionableException;
import com.flowops.aiinsight.domain.FindingKey;
import com.flowops.aiinsight.domain.InsightAction;
import com.flowops.aiinsight.domain.InsightIdentity;
import com.flowops.aiinsight.domain.InsightKind;
import com.flowops.aiinsight.domain.SubjectType;
import com.flowops.shared.published.PublishedRefusal;
import com.flowops.shared.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
@RequestMapping("/api/insights")
@Tag(name = "Insights", description = "What the history says, with the evidence for saying it")
public class InsightController {
    private final ViewInsightsUseCase insights;
    private final DecideOnInsightUseCase decisions;

    public InsightController(ViewInsightsUseCase insights, DecideOnInsightUseCase decisions) {
        this.insights = insights;
        this.decisions = decisions;
    }

    public record InsightResponse(
            String kind,
            UUID subjectId,
            String subjectName,
            String findingKey,
            String action,
            String stepTitle,
            String afterStep,
            String beforeStep,
            int occurrences,
            int runsTotal,
            ViewInsightsUseCase.Phases medianPhases,
            String reason,
            String dependsOnStep,
            Integer daysSinceLastUse,
            Integer windowDays,
            Integer expectedIntervalDays,
            Long estimatedMs,
            Long medianWorkMs,
            Long fastestMiddleMs,
            Long slowestMiddleMs,
            Integer excludedRuns,
            Integer qualifyingPopulation,
            Instant windowFrom,
            Instant windowTo,
            List<UUID> instances) {}

    public record InsightDecisionRequest(
            @NotBlank String subjectType, UUID subjectId, @NotBlank String kind, @NotBlank String findingKey) {}

    @Operation(
            summary = "What a subject's history says about it (AI-INSIGHT-MISSING-STEP-01)",
            description = "Deterministic findings over the subject's history. No model is involved and no"
                    + " provider is called. An insight whose sample is below its floor is not returned at"
                    + " all — not a hedged one, because a hedge is read as a recommendation by anybody in"
                    + " a hurry. Anything a person has already applied or dismissed is absent"
                    + " (AI-INSIGHT-ACT-ON-INSIGHT-01).")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "The findings, most frequent first. An empty list is an answer"),
        @ApiResponse(responseCode = "400", description = "An unknown subject type"),
        @ApiResponse(responseCode = "403", description = "The caller does not hold AI_INSIGHT_VIEW")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('AI_INSIGHT_VIEW')")
    public List<InsightResponse> forSubject(
            @Parameter(description = "process_template or task_template") @RequestParam("subject_type")
                    String subjectType,
            @RequestParam("subject_id") UUID subjectId) {
        return insights.forSubject(subjectOf(subjectType), subjectId).stream()
                .map(InsightController::described)
                .toList();
    }

    @Operation(
            summary = "Make the change a finding proposes (AI-INSIGHT-ACT-ON-INSIGHT-01)",
            description = "The change is made through the subject feature's own edit path, with its"
                    + " validations and its permission check. This feature grants no authority: a person"
                    + " who may read a finding about a template they cannot edit is refused here, which is"
                    + " correct — seeing that something is wrong and being allowed to change it are"
                    + " different rights. The subject feature's refusal is returned verbatim.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Applied; the finding is absent on the next read"),
        @ApiResponse(responseCode = "400", description = "An unknown subject type, or a finding that proposes nothing"),
        @ApiResponse(
                responseCode = "403",
                description = "The caller lacks AI_INSIGHT_VIEW, or the subject feature refused the edit"),
        @ApiResponse(
                responseCode = "409",
                description = "INSIGHT_NO_LONGER_HOLDS: the subject changed, or somebody applied it first")
    })
    @PostMapping("/apply")
    @PreAuthorize("hasAuthority('AI_INSIGHT_VIEW')")
    public ResponseEntity<Void> apply(@Valid @RequestBody InsightDecisionRequest request) {
        decisions.apply(identityOf(request));
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Put a finding away for good (AI-INSIGHT-ACT-ON-INSIGHT-01)",
            description = "Nothing changes anywhere except the record of the judgement. The finding does"
                    + " not return, however much the evidence grows, until the subject itself changes"
                    + " materially. Dismiss is offered on every finding, including the ones that propose"
                    + " no change — a finding nobody can act on and nobody can silence is the nagging that"
                    + " costs this feature its reader.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Dismissed; the finding is absent on the next read"),
        @ApiResponse(responseCode = "400", description = "An unknown subject type"),
        @ApiResponse(responseCode = "403", description = "The caller does not hold AI_INSIGHT_VIEW"),
        @ApiResponse(responseCode = "409", description = "INSIGHT_NO_LONGER_HOLDS: the subject changed")
    })
    @PostMapping("/dismiss")
    @PreAuthorize("hasAuthority('AI_INSIGHT_VIEW')")
    public ResponseEntity<Void> dismiss(@Valid @RequestBody InsightDecisionRequest request) {
        decisions.dismiss(identityOf(request));
        return ResponseEntity.noContent().build();
    }

    private static InsightIdentity identityOf(InsightDecisionRequest request) {
        SubjectType subjectType = subjectOf(request.subjectType());
        InsightKind kind;
        try {
            kind = InsightKind.valueOf(request.kind());
        } catch (IllegalArgumentException unknown) {
            throw new UnknownInsightKindException(request.kind());
        }
        return InsightIdentity.of(kind, subjectType, request.subjectId(), new FindingKey(request.findingKey()));
    }

    private static SubjectType subjectOf(String wireName) {
        return SubjectType.ofWireName(wireName).orElseThrow(() -> new UnknownSubjectTypeException(wireName));
    }

    private static InsightResponse described(ViewInsightsUseCase.Insight found) {
        InsightIdentity identity = found.identity();
        ViewInsightsUseCase.Evidence evidence = found.evidence();
        Flattened detail = flattened(found.detail());

        return new InsightResponse(
                identity.kind().name(),
                identity.subjectId(),
                found.subjectName(),
                identity.findingKey().value(),
                actionNameOf(found.action()),
                detail.stepTitle(),
                detail.afterStep(),
                detail.beforeStep(),
                evidence.sampleSize(),
                evidence.population(),
                detail.medianPhases(),
                detail.reason(),
                detail.dependsOnStep(),
                detail.daysSinceLastUse(),
                detail.windowDays(),
                detail.expectedIntervalDays(),
                detail.estimatedMs(),
                detail.medianWorkMs(),
                detail.fastestMiddleMs(),
                detail.slowestMiddleMs(),
                evidence.coverage() == null ? null : evidence.coverage().excluded(),
                evidence.coverage() == null ? null : evidence.coverage().qualifying(),
                evidence.windowFrom(),
                evidence.windowTo(),
                evidence.instances());
    }

    private record Flattened(
            String stepTitle,
            String afterStep,
            String beforeStep,
            ViewInsightsUseCase.Phases medianPhases,
            String reason,
            String dependsOnStep,
            Integer daysSinceLastUse,
            Integer windowDays,
            Integer expectedIntervalDays,
            Long estimatedMs,
            Long medianWorkMs,
            Long fastestMiddleMs,
            Long slowestMiddleMs) {}

    private static Flattened flattened(Detail detail) {
        return switch (detail) {
            case Detail.MissingStep missing -> new Flattened(
                    missing.title(),
                    missing.afterStep(),
                    missing.beforeStep(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
            case Detail.SlowStep slow -> new Flattened(
                    slow.title(),
                    null,
                    null,
                    slow.medianPhases(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
            case Detail.BlockPattern blocked -> new Flattened(
                    blocked.stepTitle(),
                    null,
                    null,
                    null,
                    blocked.reason(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
            case Detail.FalseDependency idle -> new Flattened(
                    idle.dependentTitle(),
                    null,
                    null,
                    null,
                    null,
                    idle.dependsOnTitle(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
            case Detail.UnusedTemplate stale -> new Flattened(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    stale.daysSinceLastUse(),
                    stale.windowDays(),
                    stale.expectedIntervalDays(),
                    null,
                    null,
                    null,
                    null);
            case Detail.EstimateDivergence gap -> new Flattened(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    gap.estimatedMs(),
                    gap.medianWorkMs(),
                    gap.fastestMiddleMs(),
                    gap.slowestMiddleMs());
        };
    }

    private static String actionNameOf(InsightAction action) {
        return switch (action) {
            case null -> null;
            case InsightAction.InsertStep ignored -> "INSERT_STEP";
            case InsightAction.RemoveDependency ignored -> "REMOVE_DEPENDENCY";
            case InsightAction.RetireTemplate ignored -> "RETIRE_TEMPLATE";
            case InsightAction.UpdateEstimate ignored -> "UPDATE_ESTIMATE";
        };
    }

    static class UnknownSubjectTypeException extends RuntimeException {
        UnknownSubjectTypeException(String subjectType) {
            super(subjectType);
        }
    }

    static class UnknownInsightKindException extends RuntimeException {
        UnknownInsightKindException(String kind) {
            super(kind);
        }
    }

    @RestControllerAdvice(basePackages = "com.flowops.aiinsight.api")
    static class InsightExceptionHandler {
        @ExceptionHandler(UnknownSubjectTypeException.class)
        ResponseEntity<ErrorResponse> onUnknownSubject(UnknownSubjectTypeException refusal) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.of(
                            "UNKNOWN_SUBJECT_TYPE",
                            "That is not a subject this product can reason about.",
                            List.of(new ErrorResponse.FieldViolation("subject_type", "UNKNOWN"))));
        }

        @ExceptionHandler(UnknownInsightKindException.class)
        ResponseEntity<ErrorResponse> onUnknownKind(UnknownInsightKindException refusal) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.of(
                            "UNKNOWN_INSIGHT_KIND",
                            "That is not a finding this product produces.",
                            List.of(new ErrorResponse.FieldViolation("kind", "UNKNOWN"))));
        }

        @ExceptionHandler(PublishedRefusal.class)
        ResponseEntity<ErrorResponse> onSubjectFeatureRefusal(PublishedRefusal refusal) {
            HttpStatus status =
                    switch (refusal.kind()) {
                        case NOT_FOUND -> HttpStatus.NOT_FOUND;
                        case NOT_PERMITTED -> HttpStatus.FORBIDDEN;
                        case CONFLICT -> HttpStatus.CONFLICT;
                    };
            return ResponseEntity.status(status)
                    .body(ErrorResponse.of(refusal.code(), refusal.getMessage(), List.of()));
        }

        @ExceptionHandler(InsightNoLongerHoldsException.class)
        ResponseEntity<ErrorResponse> onStale(InsightNoLongerHoldsException refusal) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.of(
                            "INSIGHT_NO_LONGER_HOLDS",
                            "That finding is no longer current. It has been recalculated.",
                            List.of()));
        }

        @ExceptionHandler(InsightNotActionableException.class)
        ResponseEntity<ErrorResponse> onNotActionable(InsightNotActionableException refusal) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.of(
                            "INSIGHT_PROPOSES_NO_CHANGE",
                            "That finding is informational. There is nothing to apply.",
                            List.of()));
        }

        @ExceptionHandler(AuthorizationDeniedException.class)
        ResponseEntity<ErrorResponse> onDenied(AuthorizationDeniedException refusal) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorResponse.of("NOT_PERMITTED", "You do not have permission to do that.", List.of()));
        }
    }
}
