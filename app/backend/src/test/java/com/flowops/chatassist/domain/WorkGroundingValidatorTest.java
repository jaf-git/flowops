package com.flowops.chatassist.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("CHAT-ASSIST-SUGGEST-WORK-01")
class WorkGroundingValidatorTest {
    private static final ConversationExtract HANDOVER = new ConversationExtract(List.of(
            new ConversationExtract.Line("P1", "Aurora Coffee signed the retainer this morning."),
            new ConversationExtract.Line("P2", "That is good news. Do we start with the workshop?"),
            new ConversationExtract.Line(
                    "P1",
                    "Yes — discovery workshop first, then the competitor scan, then positioning, and we hand"
                            + " the messaging framework to Content."),
            new ConversationExtract.Line("P2", "I can take the workshop.")));

    private static final ConversationExtract ABOUT_THURSDAY = new ConversationExtract(List.of(
            new ConversationExtract.Line("P1", "Are you in on Thursday?"),
            new ConversationExtract.Line("P2", "All day, yes."),
            new ConversationExtract.Line("P1", "Good. Let us do the quarterly review then.")));

    private static WorkOpinion sawAProcess(String... phrases) {
        return saw(WorkShape.PROCESS, phrases);
    }

    private static WorkOpinion sawATask(String... phrases) {
        return saw(WorkShape.TASK, phrases);
    }

    private static WorkOpinion saw(WorkShape shape, String... phrases) {
        return new WorkOpinion(
                shape,
                List.of(phrases).stream()
                        .map(phrase -> new WorkOpinion.Part(phrase, null))
                        .toList(),
                "thinking out loud");
    }

    @Test
    void proposesTheStepsInTheConversationsOwnWords() {
        var work = WorkGroundingValidator.check(
                sawAProcess("discovery workshop", "competitor scan", "positioning", "messaging framework"), HANDOVER);

        assertThat(work).isPresent();
        assertThat(work.orElseThrow().parts())
                .extracting(WorkGroundingValidator.GroundedPart::span)
                .as("their characters, in the order they said them")
                .containsExactly("discovery workshop", "competitor scan", "positioning", "messaging framework");
    }

    @Test
    void answersWithTheConversationsCasingRatherThanTheModels() {
        var work = WorkGroundingValidator.check(
                sawAProcess("AURORA COFFEE", "discovery WORKSHOP", "COMPETITOR scan"), HANDOVER);

        assertThat(work.orElseThrow().parts())
                .extracting(WorkGroundingValidator.GroundedPart::span)
                .containsExactly("Aurora Coffee", "discovery workshop", "competitor scan");
    }

    @Test
    void dropsAPhraseNobodySaid() {
        var work = WorkGroundingValidator.check(
                sawAProcess("discovery workshop", "competitor scan", "budget approval", "positioning"), HANDOVER);

        assertThat(work.orElseThrow().parts())
                .extracting(WorkGroundingValidator.GroundedPart::span)
                .as("nobody mentioned a budget, so there is no step about one")
                .containsExactly("discovery workshop", "competitor scan", "positioning");
    }

    @Test
    void suppressesEverythingWhenMoreThanHalfTheAnswerFailsToCheck() {
        var work = WorkGroundingValidator.check(
                sawAProcess("discovery workshop", "competitor scan", "budget sign-off", "legal review", "media buy"),
                HANDOVER);

        assertThat(work).isEmpty();
    }

    @Test
    void collapsesTwoPhrasesThatResolveToTheSameWords() {
        var work = WorkGroundingValidator.check(
                sawAProcess("discovery workshop", "Discovery Workshop", "competitor scan", "positioning"), HANDOVER);

        assertThat(work.orElseThrow().parts())
                .extracting(WorkGroundingValidator.GroundedPart::span)
                .as("a thread that repeats itself is not a process with a duplicated step")
                .containsExactly("discovery workshop", "competitor scan", "positioning");
    }

    @Test
    void refusesToCallOneStepAProcess() {
        var work = WorkGroundingValidator.check(sawAProcess("discovery workshop"), HANDOVER);

        assertThat(work)
                .as("one step is a task, and turning a message into one already has a control")
                .isEmpty();
    }

    @Test
    void acceptsTwoPartsAsAChecklistAndRefusesThemAsAProcess() {
        var asChecklist = WorkGroundingValidator.check(sawATask("discovery workshop", "competitor scan"), HANDOVER);
        var asProcess = WorkGroundingValidator.check(sawAProcess("discovery workshop", "competitor scan"), HANDOVER);

        assertThat(asChecklist).isPresent();
        assertThat(asChecklist.orElseThrow().shape()).isEqualTo(WorkShape.TASK);
        assertThat(asChecklist.orElseThrow().shape()).isNotEqualTo(WorkShape.PROCESS);
        assertThat(asProcess).isEmpty();
    }

    @Test
    void refusesOnePartWhateverShapeTheModelClaimed() {
        assertThat(WorkGroundingValidator.check(sawATask("discovery workshop"), HANDOVER))
                .isEmpty();
        assertThat(WorkGroundingValidator.check(sawAProcess("discovery workshop"), HANDOVER))
                .isEmpty();
    }

    @Test
    void carriesTheShapeTheModelChose() {
        var work = WorkGroundingValidator.check(
                sawAProcess("discovery workshop", "competitor scan", "positioning"), HANDOVER);

        assertThat(work.orElseThrow().shape()).isEqualTo(WorkShape.PROCESS);
    }

    @Test
    void refusesToCallTwoPhrasesFromAChatAProcess() {
        var work = WorkGroundingValidator.check(sawAProcess("discovery workshop", "competitor scan"), HANDOVER);

        assertThat(work).isEmpty();
    }

    @Test
    void refusesAPhraseThatOnlyExistsAcrossTwoMessages() {
        var work = WorkGroundingValidator.check(
                sawAProcess(
                        "signed the retainer this morning. That is good news",
                        "discovery workshop",
                        "competitor scan",
                        "positioning"),
                HANDOVER);

        assertThat(work.orElseThrow().parts())
                .extracting(WorkGroundingValidator.GroundedPart::span)
                .as("a step is quoted from one thing one person said, or it is not quoted at all")
                .containsExactly("discovery workshop", "competitor scan", "positioning");
    }

    @Test
    void saysNothingWhenTheModelSawNoProcess() {
        var work = WorkGroundingValidator.check(saw(WorkShape.NOTHING, "quarterly review"), HANDOVER);

        assertThat(work).isEmpty();
    }

    @Test
    void findsNothingInAConversationAboutThursday() {
        var work = WorkGroundingValidator.check(
                sawAProcess("book the room", "prepare the deck", "send the agenda"), ABOUT_THURSDAY);

        assertThat(work).isEmpty();
    }

    @Test
    void saysNothingAboutAnEmptyConversation() {
        var work = WorkGroundingValidator.check(sawAProcess("anything"), new ConversationExtract(List.of()));

        assertThat(work).isEmpty();
    }

    @Test
    void refusesAPhraseTooShortToMeanAnything() {
        var work = WorkGroundingValidator.check(sawAProcess("a", "e", "discovery workshop"), HANDOVER);

        assertThat(work).isEmpty();
    }

    @Test
    void survivesAModelThatReturnedNothingAtAll() {
        assertThat(WorkGroundingValidator.check(WorkOpinion.silent(), HANDOVER)).isEmpty();
        assertThat(WorkGroundingValidator.check(null, HANDOVER)).isEmpty();
    }
}
