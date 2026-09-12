package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.domain.CandidateTemplate;
import com.flowops.nodepipeline.domain.compose.Conversion;
import com.flowops.nodepipeline.domain.discovery.StepKind;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("NODEPIPE-AI-01")
class TheModelMayOrderWordsItMayNotInventThemTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 31);

    @Test
    void aLabelBuiltFromTheMarksOwnWordsIsUsed() {
        CandidateTemplate minted = Conversion.mint(aKind(), List.of(), "T-1", TODAY, kind -> "review captions");

        assertThat(minted.title())
                .as("every content word appears in the marks, so the model only ordered them")
                .isEqualTo("review captions");
    }

    @Test
    void anInventedWordIsRefusedAndTheFrequencyNameAnswers() {
        CandidateTemplate invented =
                Conversion.mint(aKind(), List.of(), "T-1", TODAY, kind -> "orchestrate deliverables");
        CandidateTemplate deterministic = Conversion.mint(aKind(), List.of(), "T-1", TODAY, null);

        assertThat(invented.title())
                .as("neither word is in the marks, so the model composed rather than selected")
                .isEqualTo(deterministic.title());
    }

    @Test
    void aSilentModelLeavesTheNameExactlyAsItWas() {
        assertThat(Conversion.mint(aKind(), List.of(), "T-1", TODAY, kind -> null)
                        .title())
                .isEqualTo(
                        Conversion.mint(aKind(), List.of(), "T-1", TODAY, null).title());
    }

    @Test
    void theShippedDefaultNeverAsksAnything() {
        assertThat(Conversion.mint(aKind(), List.of(), "T-1", TODAY, null).title())
                .as("the frequency label, unchanged by anything a model might have said")
                .contains("captions");
    }

    private static StepKind aKind() {
        return new StepKind(
                "K:WRITER:TEXT",
                "WRITER",
                "TEXT",
                null,
                List.of("n1", "n2", "n3"),
                java.util.Set.of("J1", "J2"),
                List.of("captions", "review", "pass"),
                0.8,
                0.7);
    }
}
