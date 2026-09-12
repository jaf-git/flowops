package com.flowops.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnInterrogativeNameIsNotAQuestionTest {
    @Test
    @DisplayName("a finished sentence about work named How we work is work")
    void aNamedThingIsNotAQuestion() {
        assertThat(Intent.of("How we work is live on the blog with the link out to the accounts.", null))
                .isEqualTo(Intent.WORK);

        assertThat(Intent.of("What we do has been rewritten and signed off.", null))
                .isEqualTo(Intent.WORK);
        assertThat(Intent.of("Where to find us is scheduled for Tuesday morning.", null))
                .isEqualTo(Intent.WORK);
        assertThat(Intent.of("Why it matters went live with the campaign.", null))
                .isEqualTo(Intent.WORK);
    }

    @Test
    @DisplayName("the unpunctuated shout the opener rule exists for is still a question")
    void anOpenerWithoutATerminatorStillAsks() {
        assertThat(Intent.of("WHERE ARE THE GROCER CAPTIONS", null)).isEqualTo(Intent.QUESTION);
        assertThat(Intent.of("how is the rain shell going", null)).isEqualTo(Intent.QUESTION);
    }

    @Test
    @DisplayName("a question mark decides it whatever else is true")
    void theMarkStillWins() {
        assertThat(Intent.of("How we work is live on the blog?", null)).isEqualTo(Intent.QUESTION);
    }

    @Test
    @DisplayName("the terminator is read from the message, not from the message plus its detail")
    void theDetailDoesNotTerminateTheMessage() {
        assertThat(Intent.of("how is the rain shell going", "Chased Radu about it yesterday."))
                .isEqualTo(Intent.QUESTION);
    }

    @Test
    @DisplayName("negation and meta still come first")
    void theOrderOfTheRulesIsUnchanged() {
        assertThat(Intent.of("should we skip the captions?", null)).isEqualTo(Intent.NEGATED);
        assertThat(Intent.of("which template covers this", null)).isEqualTo(Intent.META);
    }
}
