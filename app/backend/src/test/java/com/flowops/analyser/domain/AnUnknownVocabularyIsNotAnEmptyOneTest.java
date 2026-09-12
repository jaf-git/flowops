package com.flowops.analyser.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.analyser.application.analysers.LibraryCoverage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnUnknownVocabularyIsNotAnEmptyOneTest {
    private static final Instant FROM = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-30T09:00:00Z");

    @Test
    @DisplayName("with no vocabulary read, nothing is reported as outside it")
    void silenceWhenTheVocabularyIsUnknown() {
        Report report = new LibraryCoverage().analyse(windowOf(List.of()));

        assertThat(kinds(report))
                .as("an unread vocabulary must not make every kind of work a violation")
                .doesNotContain("work_kind_outside_the_vocabulary");
    }

    @Test
    @DisplayName("with a vocabulary that does not admit the work, it is reported")
    void spokenWhenTheVocabularyIsKnownAndDisagrees() {
        Report report = new LibraryCoverage().analyse(windowOf(List.of("CONTENT")));

        assertThat(kinds(report))
                .as("a kind of work the library structurally cannot describe is worth saying")
                .contains("work_kind_outside_the_vocabulary");
    }

    private static List<String> kinds(Report report) {
        return report.findings().stream().map(Finding::kind).toList();
    }

    private static Snapshot windowOf(List<String> governed) {
        return new Snapshot(
                FROM,
                TO,
                List.of(),
                List.of(bracket("CONTENT"), bracket("STRATEGY")),
                List.of(),
                List.of(),
                List.of(),
                Snapshot.Shapes.none(),
                governed);
    }

    private static Snapshot.Bracket bracket(String workType) {
        return new Snapshot.Bracket(
                java.util.UUID.randomUUID().toString(), null, workType, "DELIVERED", "TEXT", FROM, TO);
    }
}
