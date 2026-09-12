package com.flowops.discovery.api.dto;

import com.flowops.discovery.application.digest.WeeklyDigestUseCase.Decision;
import com.flowops.discovery.application.digest.WeeklyDigestUseCase.Digest;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "WeeklyDigest", description = "What the week produced, and the at most three things worth deciding.")
public record DigestResponse(
        @Schema(description = "Threads of work that ended in the last seven days.") int closedThisWeek,
        @Schema(
                        description =
                                """
                        Discovered types waiting for a name. One of three tiles describing the week, not
                        a badge: it is bounded by the catalogue rather than by an inbox, and it goes down
                        as they are answered.
                        """)
                int proposals,
        @Schema(
                        description =
                                """
                        Threshold 6's figure: the share of the people who hold a stated job who marked
                        any work this week. 100 where nobody has a stated job yet — there is nobody who
                        could have clicked, so there is nobody who has stopped.
                        """)
                int coveragePercent,
        @Schema(description = "At most three, ever, ranked by value consumed rather than by recency or count.")
                List<DecisionRow> decisions) {
    public static DigestResponse of(Digest digest) {
        return new DigestResponse(
                digest.closedThisWeek(),
                digest.proposals(),
                digest.coveragePercent(),
                digest.decisions().stream().map(DecisionRow::of).toList());
    }

    @Schema(name = "WeeklyDecision", description = "One decision the week is asking for.")
    public record DecisionRow(
            @Schema(
                            description =
                                    """
                            CONFIRM_A_NAME — a shape has repeated often enough to be worth a word.
                            A_ROLE_STOPPED_CLICKING — a role's people have gone quiet, which weakens every
                            type resting on that role. The second is the ONE Provisional-adjacent row the
                            digest may carry, and it is keyed to the role and never to a person.
                            """)
                    String kind,
            @Schema(description = "The type this decision is about. Absent on a row about a role.") UUID typeId,
            @Schema(
                            description = "What the decision is about, in roles: \"Account manager → Content writer\", "
                                    + "or the role's own name.")
                    String subject,
            @Schema(description = "Counter C2 for a proposal. Zero on a row about a role, which counts no threads.")
                    int occurrenceCount) {
        public static DecisionRow of(Decision decision) {
            return new DecisionRow(
                    decision.kind().name(), decision.typeId(), decision.subject(), decision.occurrenceCount());
        }
    }
}
