package com.flowops.analyser.api.dto;

import com.flowops.analyser.application.viewfindings.ViewFindingsUseCase;
import com.flowops.analyser.domain.FindingContext;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FindingQueueResponse(
        UUID runId,
        @Schema(description = "The span the analysers looked at, so a small answer is not read as a broken one")
                Instant windowFrom,
        Instant windowTo,
        Instant ranAt,
        List<Group> groups,
        Standing standing) {
    public record Group(String category, List<Item> items) {}

    public record Item(
            UUID id,
            String analyser,
            String kind,
            @Schema(
                            description = "Which question the analyser answered: OBSERVE, MEASURE, DETECT, CORRELATE "
                                    + "or RECOMMEND. ANALYSE on every finding written before stages were real")
                    String stage,
            String subjectKind,
            @Schema(description = "The clusterer's own key. Identity across runs — never render it") String subject,
            @Schema(description = "What the subject is called. Render this") String subjectName,
            @Schema(description = "What the finding rests on. Null where it rests on nothing nameable") Context context,
            String headline,
            List<String> because,
            String severity,
            String confidence,
            int reach,
            @Schema(description = "What reach is out of. Null where the count stands on its own") Integer reachOf,
            String action,
            String lifecycle,
            @Schema(description = "Consecutive runs this has appeared in, which is what fades it") int timesSeen,
            Instant firstSeenAt,
            double priority,
            String whyItRanks,
            List<Evidence> evidence) {}

    public record Evidence(String kind, String id) {}

    public record Context(
            List<String> clients,
            List<String> projects,
            int engagements,
            List<String> workTypes,
            Instant from,
            Instant to) {
        static Context of(FindingContext context) {
            return context == null || context.isEmpty()
                    ? null
                    : new Context(
                            context.clients(),
                            context.projects(),
                            context.engagements(),
                            context.workTypes(),
                            context.from(),
                            context.to());
        }
    }

    public record Standing(
            int shown, int fresh, int worsening, int stillTrue, int improving, int dismissed, boolean nothingNew) {}

    public static FindingQueueResponse of(ViewFindingsUseCase.Queue queue) {
        return new FindingQueueResponse(
                queue.runId(),
                queue.windowFrom(),
                queue.windowTo(),
                queue.ranAt(),
                queue.groups().stream()
                        .map(group -> new Group(
                                group.category().name(),
                                group.items().stream()
                                        .map(FindingQueueResponse::itemOf)
                                        .toList()))
                        .toList(),
                standingOf(queue.standing()));
    }

    private static Item itemOf(ViewFindingsUseCase.Item item) {
        return new Item(
                item.id(),
                item.analyser(),
                item.kind(),
                item.stage(),
                item.subjectKind(),
                item.subject(),
                item.subjectName(),
                Context.of(item.context()),
                item.headline(),
                item.because(),
                item.severity() == null ? null : item.severity().name(),
                item.confidence() == null ? null : item.confidence().name(),
                item.reach(),
                item.reachOf(),
                item.action(),
                item.lifecycle().name(),
                item.timesSeen(),
                item.firstSeenAt(),
                item.priority(),
                item.whyItRanks(),
                item.evidence().stream()
                        .map(evidence -> new Evidence(evidence.kind(), evidence.id()))
                        .toList());
    }

    private static Standing standingOf(ViewFindingsUseCase.Standing standing) {
        return new Standing(
                standing.shown(),
                standing.fresh(),
                standing.worsening(),
                standing.stillTrue(),
                standing.improving(),
                standing.dismissed(),
                standing.nothingNew());
    }
}
