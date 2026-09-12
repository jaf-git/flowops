package com.flowops.aiassist.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroundingValidatorTest {
    private static final EvidencePacket ONBOARDING = EvidencePacket.about(
            "Onboarding client nou",
            "Strânge brieful, trimite contractul, programează kickoff-ul.",
            List.of(
                    "Strânge brieful de la client",
                    "Trimite contractul spre semnare",
                    "Predă echipei de producție",
                    "Programează kickoff-ul"));

    private static ShapeOpinion sawAProcess(String reasoning, String... keys) {
        return new ShapeOpinion(true, List.of(keys), reasoning);
    }

    @Test
    @DisplayName("the words come from the template, never from the model")
    void wordsComeFromTheEvidence() {
        Optional<GroundedSuggestion> checked = GroundingValidator.check(
                sawAProcess("Munca trece de la o persoană la alta.", "c1", "c2", "c3", "c4"), ONBOARDING);

        assertThat(checked).isPresent();
        assertThat(checked.get().steps())
                .extracting(GroundedSuggestion.Step::text)
                .containsExactly(
                        "Strânge brieful de la client",
                        "Trimite contractul spre semnare",
                        "Predă echipei de producție",
                        "Programează kickoff-ul");
        assertThat(checked.get().partial()).isFalse();
    }

    @Test
    @DisplayName("a step quoting a key that is not there is dropped, and the rest survives")
    void ungroundedStepsAreDropped() {
        Optional<GroundedSuggestion> checked =
                GroundingValidator.check(sawAProcess("Trece de la unul la altul.", "c1", "c2", "c9"), ONBOARDING);

        assertThat(checked).isPresent();
        assertThat(checked.get().steps())
                .extracting(GroundedSuggestion.Step::sourceKey)
                .containsExactly("c1", "c2");

        assertThat(checked.get().partial()).isTrue();
    }

    @Test
    @DisplayName("more than half ungrounded suppresses the whole suggestion")
    void mostlyUngroundedIsNoSuggestion() {
        assertThat(GroundingValidator.check(sawAProcess("Pare un proces.", "c1", "c8", "c9"), ONBOARDING))
                .isEmpty();
    }

    @Test
    @DisplayName("nothing the model wrote reaches the suggestion")
    void everyWordIsSomebodyElses() {
        Optional<GroundedSuggestion> checked = GroundingValidator.check(
                sawAProcess("This has 4 steps and takes 3 hours; call the first one Client brief.", "c1", "c2"),
                ONBOARDING);

        assertThat(checked).isPresent();

        List<String> everyString = checked.get().steps().stream()
                .flatMap(step -> java.util.stream.Stream.of(step.sourceKey(), step.text()))
                .toList();

        for (String value : everyString) {
            boolean fromTheEvidence = ONBOARDING.textOf(value).isPresent()
                    || ONBOARDING.lines().stream().anyMatch(line -> line.text().equals(value));
            assertThat(fromTheEvidence)
                    .as("%s is not something anybody in the workspace wrote", value)
                    .isTrue();
        }

        assertThat(everyString).noneMatch(value -> value.contains("Client brief"));

        assertThat(checked.get().evidenceLines()).isEqualTo(4);
    }

    @Test
    @DisplayName("the same item proposed twice becomes one step")
    void repeatsCollapse() {
        Optional<GroundedSuggestion> checked =
                GroundingValidator.check(sawAProcess("Trece între oameni.", "c1", "c1", "c2"), ONBOARDING);

        assertThat(checked).isPresent();
        assertThat(checked.get().steps())
                .extracting(GroundedSuggestion.Step::sourceKey)
                .containsExactly("c1", "c2");
    }

    @Test
    @DisplayName("no process seen is no suggestion, which is the ordinary answer")
    void sawNoProcess() {
        assertThat(GroundingValidator.check(new ShapeOpinion(false, List.of(), "O singură persoană."), ONBOARDING))
                .isEmpty();
    }

    @Test
    @DisplayName("one step is not a process, because that is the task the template already is")
    void oneStepIsNotAProcess() {
        assertThat(GroundingValidator.check(sawAProcess("Un singur pas.", "c1"), ONBOARDING))
                .isEmpty();
    }

    @Test
    @DisplayName("a truncated packet is reported as partial even when every step checks out")
    void truncationIsPartialToo() {
        List<String> tooMany = java.util.stream.IntStream.rangeClosed(1, EvidencePacket.MOST_LINES + 5)
                .mapToObj(number -> "Pasul " + number)
                .toList();
        EvidencePacket bounded = EvidencePacket.about("Lung", null, tooMany);

        assertThat(bounded.lines()).hasSize(EvidencePacket.MOST_LINES);
        assertThat(bounded.wasTruncated()).isTrue();

        Optional<GroundedSuggestion> checked =
                GroundingValidator.check(sawAProcess("Trece între oameni.", "c1", "c2"), bounded);

        assertThat(checked).isPresent();
        assertThat(checked.get().partial()).isTrue();
    }
}
