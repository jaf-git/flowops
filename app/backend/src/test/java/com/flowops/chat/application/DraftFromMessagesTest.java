package com.flowops.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.chat.application.buildprocess.DraftFromMessages;
import com.flowops.chat.application.buildprocess.FieldSource;
import com.flowops.chat.application.buildprocess.ProcessDraft;
import com.flowops.chat.application.buildprocess.TooFewMessagesException;
import com.flowops.chat.application.buildprocess.TooManyMessagesException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("CHAT-BUILD-PROCESS-FROM-MESSAGES-01")
class DraftFromMessagesTest {
    private static final Instant NOW = Instant.parse("2026-08-21T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final UUID IONUT = UUID.randomUUID();
    private static final UUID RALUCA = UUID.randomUUID();
    private static final UUID OUTSIDER = UUID.randomUUID();

    private static DraftFromMessages.Ticked ticked(long seq, UUID author, String body) {
        return new DraftFromMessages.Ticked(UUID.randomUUID(), author, body, seq);
    }

    private static ProcessDraft draftOf(List<DraftFromMessages.Ticked> messages) {
        return new DraftFromMessages(CLOCK).assemble(messages, IONUT, Set.of(IONUT, RALUCA));
    }

    @Test
    void theRunTakesItsNameFromTheEarliestThingSaid() {
        ProcessDraft draft = draftOf(List.of(
                ticked(9, RALUCA, "Contract"),
                ticked(3, IONUT, "Măsurători la fața locului"),
                ticked(7, RALUCA, "Ofertă")));

        assertThat(draft.name().value()).isEqualTo("Măsurători la fața locului");
        assertThat(draft.name().source())
                .as("somebody typed it, so it is quoted rather than suggested")
                .isEqualTo(FieldSource.FROM_CONVERSATION);
    }

    @Test
    void stepsFollowTheThreadRatherThanTheOrderTheyWereTicked() {
        ProcessDraft draft = draftOf(
                List.of(ticked(9, RALUCA, "Contract"), ticked(3, IONUT, "Măsurători"), ticked(7, RALUCA, "Ofertă")));

        assertThat(draft.steps())
                .extracting(step -> step.title().value())
                .containsExactly("Măsurători", "Ofertă", "Contract");
    }

    @Test
    void aStepQuotesItsMessageAndKeepsTheWholeOfItAsTheDescription() {
        String body = "Ofertă\nCu preț ferm și termen de livrare";

        ProcessDraft draft = draftOf(List.of(ticked(1, RALUCA, "Măsurători"), ticked(2, RALUCA, body)));

        assertThat(draft.steps().get(1).title().value())
                .as("a title is a line; the rest is not lost, it is below")
                .isEqualTo("Ofertă");
        assertThat(draft.steps().get(1).description()).isEqualTo(body);
        assertThat(draft.steps().get(1).title().source()).isEqualTo(FieldSource.FROM_CONVERSATION);
    }

    @Test
    void aVeryLongLineIsCutForTheTitleAndKeptInFull() {
        String sprawling = "x".repeat(400);

        ProcessDraft draft = draftOf(List.of(ticked(1, RALUCA, sprawling), ticked(2, RALUCA, "Ofertă")));

        assertThat(draft.steps().get(0).title().value()).hasSize(120);
        assertThat(draft.steps().get(0).description()).isEqualTo(sprawling);
    }

    @Test
    void aStepIsOfferedToWhoeverSaidItWhenTheActorMayDirectThem() {
        ProcessDraft draft = draftOf(List.of(ticked(1, RALUCA, "Măsurători"), ticked(2, IONUT, "Ofertă")));

        assertThat(draft.steps().get(0).assigneeId().value()).isEqualTo(RALUCA);
        assertThat(draft.steps().get(0).assigneeId().source()).isEqualTo(FieldSource.SUGGESTED);
        assertThat(draft.steps().get(1).assigneeId().value()).isEqualTo(IONUT);
    }

    @Test
    void aStepSaidBySomebodyIMayNotDirectFallsBackToMe() {
        ProcessDraft draft = draftOf(List.of(ticked(1, OUTSIDER, "Măsurători"), ticked(2, RALUCA, "Ofertă")));

        assertThat(draft.steps().get(0).assigneeId().value())
                .as("not the outsider, because the whole run would be refused for them")
                .isEqualTo(IONUT);
        assertThat(draft.steps().get(0).assigneeId().source()).isEqualTo(FieldSource.SUGGESTED);
    }

    @Test
    void datesAreSpacedThreeDaysApartAndEveryOneIsMarkedAGuess() {
        ProcessDraft draft = draftOf(
                List.of(ticked(1, RALUCA, "Măsurători"), ticked(2, RALUCA, "Ofertă"), ticked(3, RALUCA, "Contract")));

        assertThat(draft.steps())
                .extracting(step -> step.deadline().value())
                .containsExactly(
                        NOW.plus(java.time.Duration.ofDays(3)),
                        NOW.plus(java.time.Duration.ofDays(6)),
                        NOW.plus(java.time.Duration.ofDays(9)));
        assertThat(draft.steps())
                .allSatisfy(step -> assertThat(step.deadline().source()).isEqualTo(FieldSource.SUGGESTED));
    }

    @Test
    void theActorIsOfferedAsTheOneWhoSteersIt() {
        ProcessDraft draft = draftOf(List.of(ticked(1, RALUCA, "Măsurători"), ticked(2, RALUCA, "Ofertă")));

        assertThat(draft.processOwnerId().value()).isEqualTo(IONUT);
        assertThat(draft.processOwnerId().source()).isEqualTo(FieldSource.SUGGESTED);
    }

    @Test
    void oneMessageIsNotAProcess() {
        assertThatThrownBy(() -> draftOf(List.of(ticked(1, RALUCA, "Măsurători"))))
                .isInstanceOf(TooFewMessagesException.class);
        assertThatThrownBy(() -> draftOf(List.of())).isInstanceOf(TooFewMessagesException.class);
    }

    @Test
    void twoMessagesAre() {
        assertThat(draftOf(List.of(ticked(1, RALUCA, "Măsurători"), ticked(2, RALUCA, "Ofertă")))
                        .steps())
                .hasSize(2);
    }

    @Test
    void fiftyOneIsRefusedWithTheCapNamed() {
        List<DraftFromMessages.Ticked> tooMany = IntStream.rangeClosed(1, 51)
                .mapToObj(at -> ticked(at, RALUCA, "Pas " + at))
                .toList();

        assertThatThrownBy(() -> draftOf(tooMany))
                .isInstanceOf(TooManyMessagesException.class)
                .hasMessageContaining("50");
    }

    @Test
    void fiftyIsAllowed() {
        List<DraftFromMessages.Ticked> exactly = IntStream.rangeClosed(1, 50)
                .mapToObj(at -> ticked(at, RALUCA, "Pas " + at))
                .toList();

        assertThat(draftOf(exactly).steps()).hasSize(50);
    }

    @Test
    void everyTitleIsASliceOfAMessageAndNothingElseIsEverAdded() {
        List<DraftFromMessages.Ticked> messages =
                List.of(ticked(1, RALUCA, "Măsurători"), ticked(2, IONUT, "Ofertă"), ticked(3, RALUCA, "Contract"));

        ProcessDraft draft = draftOf(messages);

        assertThat(draft.steps()).allSatisfy(step -> assertThat(messages)
                .as("a title nobody typed would mean this path had learned to write")
                .anySatisfy(
                        said -> assertThat(said.body()).startsWith(step.title().value())));
    }
}
