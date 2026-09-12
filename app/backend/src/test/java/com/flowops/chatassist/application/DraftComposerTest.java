package com.flowops.chatassist.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.chatassist.domain.ConversationExtract;
import com.flowops.chatassist.domain.FieldSource;
import com.flowops.chatassist.domain.ProposedWork;
import com.flowops.chatassist.domain.WorkGroundingValidator;
import com.flowops.chatassist.domain.WorkShape;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("CHAT-ASSIST-SUGGEST-WORK-01")
class DraftComposerTest {
    private static final Instant NOW = Instant.parse("2026-08-21T09:00:00Z");
    private static final UUID RALUCA = UUID.randomUUID();
    private static final UUID IONUT = UUID.randomUUID();

    private final DraftComposer composer = new DraftComposer(Clock.fixed(NOW, ZoneOffset.UTC));

    private static final ConversationExtract HANDOVER = new ConversationExtract(List.of(
            new ConversationExtract.Line("P1", "Aurora Coffee signed the retainer this morning."),
            new ConversationExtract.Line("P2", "I can take the workshop.")));

    private static final Map<String, UUID> SPEAKERS = Map.of("P1", IONUT, "P2", RALUCA);

    private static WorkGroundingValidator.Grounded grounded(WorkShape shape, String... spanAndOwner) {
        List<WorkGroundingValidator.GroundedPart> parts = new java.util.ArrayList<>();
        for (int at = 0; at < spanAndOwner.length; at += 2) {
            parts.add(new WorkGroundingValidator.GroundedPart(spanAndOwner[at], spanAndOwner[at + 1]));
        }
        return new WorkGroundingValidator.Grounded(shape, parts);
    }

    @Test
    void givesAStepToWhoeverSaidTheyWouldDoIt() {
        ProposedWork draft = composer.compose(
                grounded(WorkShape.PROCESS, "discovery workshop", "P2", "competitor scan", "P1"),
                HANDOVER,
                SPEAKERS,
                IONUT);

        assertThat(draft.steps().get(0).assigneeId().value()).isEqualTo(RALUCA);
        assertThat(draft.steps().get(0).assigneeId().source())
                .as("she said so, which is a reason a person can check against the thread")
                .isEqualTo(FieldSource.FROM_CONVERSATION);
        assertThat(draft.steps().get(1).assigneeId().value()).isEqualTo(IONUT);
    }

    @Test
    void offersAnUnclaimedStepToWhoeverAsked() {
        ProposedWork draft = composer.compose(
                grounded(WorkShape.PROCESS, "discovery workshop", null, "competitor scan", null),
                HANDOVER,
                SPEAKERS,
                IONUT);

        assertThat(draft.steps())
                .allSatisfy(step -> assertThat(step.assigneeId().source()).isEqualTo(FieldSource.SUGGESTED));
        assertThat(draft.steps().get(0).assigneeId().value()).isEqualTo(IONUT);
    }

    @Test
    void ignoresASpeakerLabelNobodyInTheThreadHas() {
        ProposedWork draft =
                composer.compose(grounded(WorkShape.PROCESS, "discovery workshop", "P7"), HANDOVER, SPEAKERS, IONUT);

        assertThat(draft.steps().get(0).assigneeId().value()).isEqualTo(IONUT);
        assertThat(draft.steps().get(0).assigneeId().source()).isEqualTo(FieldSource.SUGGESTED);
    }

    @Test
    void spacesTheDatesAndCallsThemSuggestions() {
        ProposedWork draft = composer.compose(
                grounded(WorkShape.PROCESS, "discovery workshop", "P2", "competitor scan", "P1"),
                HANDOVER,
                SPEAKERS,
                IONUT);

        assertThat(draft.steps().get(0).deadline().value()).isEqualTo(NOW.plus(java.time.Duration.ofDays(3)));
        assertThat(draft.steps().get(1).deadline().value()).isEqualTo(NOW.plus(java.time.Duration.ofDays(6)));
        assertThat(draft.steps())
                .allSatisfy(step -> assertThat(step.deadline().source()).isEqualTo(FieldSource.SUGGESTED));
    }

    @Test
    void leavesChecklistItemsWithoutOwnersOfTheirOwn() {
        ProposedWork draft = composer.compose(
                grounded(WorkShape.TASK, "discovery workshop", "P2", "competitor scan", "P2"),
                HANDOVER,
                SPEAKERS,
                IONUT);

        assertThat(draft.isProcess()).isFalse();
        assertThat(draft.steps()).allSatisfy(item -> {
            assertThat(item.assigneeId()).isNull();
            assertThat(item.deadline()).isNull();
        });
        assertThat(draft.assigneeId().value())
                .as("the task itself belongs to whoever said they would do the first part")
                .isEqualTo(RALUCA);
    }

    @Test
    void putsTheRequesterInChargeOfARun() {
        ProposedWork draft = composer.compose(
                grounded(WorkShape.PROCESS, "discovery workshop", "P2", "competitor scan", "P2"),
                HANDOVER,
                SPEAKERS,
                IONUT);

        assertThat(draft.assigneeId().value()).isEqualTo(IONUT);
        assertThat(draft.assigneeId().source()).isEqualTo(FieldSource.SUGGESTED);
    }

    @Test
    void namesItAfterTheFirstThingAnybodySaid() {
        ProposedWork draft = composer.compose(
                grounded(WorkShape.PROCESS, "discovery workshop", "P2", "competitor scan", "P1"),
                HANDOVER,
                SPEAKERS,
                IONUT);

        assertThat(draft.title().value()).isEqualTo("Aurora Coffee signed the retainer this morning.");
        assertThat(draft.title().source()).isEqualTo(FieldSource.FROM_CONVERSATION);
    }

    @Test
    void trimsAnOpeningLineTooLongToBeAName() {
        ConversationExtract rambling =
                new ConversationExtract(List.of(new ConversationExtract.Line("P1", "x".repeat(400))));

        ProposedWork draft = composer.compose(grounded(WorkShape.PROCESS, "x", "P1"), rambling, SPEAKERS, IONUT);

        assertThat(draft.title().value().length()).isLessThanOrEqualTo(120);
    }
}
