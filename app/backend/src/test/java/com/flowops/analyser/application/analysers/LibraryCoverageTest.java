package com.flowops.analyser.application.analysers;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.analyser.domain.Clean;
import com.flowops.analyser.domain.Finding;
import com.flowops.analyser.domain.Report;
import com.flowops.analyser.domain.Snapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ANALYSER-RUN-01")
class LibraryCoverageTest {
    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-01T00:00:00Z");

    private final LibraryCoverage s6 = new LibraryCoverage();

    private static Snapshot.Template template(String id, String title, String workType, int timesUsed) {
        return new Snapshot.Template(id, title, workType, "APPROVED", timesUsed);
    }

    private static Snapshot.Bracket bracket(String id, String workType) {
        return new Snapshot.Bracket(id, "job-1", workType, "DELIVERED", "LINK", FROM, TO);
    }

    private static Snapshot of(List<Snapshot.Template> templates, List<Snapshot.Bracket> brackets) {
        return new Snapshot(FROM, TO, List.of(), brackets, List.of(), List.of(), templates, Snapshot.Shapes.none());
    }

    private static Finding findingOf(Report report, String kind) {
        return report.findings().stream()
                .filter(finding -> finding.kind().equals(kind))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + kind + " finding in " + report.findings()));
    }

    @Test
    void anEmptyLibraryIsAStatedPreconditionRatherThanAConfidentZero() {
        Report report = s6.analyse(of(List.of(), List.of()));

        assertThat(report.findings()).isEmpty();
        assertThat(report.wasBlocked()).isTrue();
        assertThat(report.preconditions().getFirst().met()).isFalse();
        assertThat(report.saysSomething()).isTrue();
    }

    @Test
    void templatesThatHaveNeverMatchedWorkAreNamedWithTheirIds() {
        Snapshot snapshot = of(
                List.of(
                        template("t1", "Caption set", "CONTENT", 7),
                        template("t2", "Quarterly retro", "TEAM_LEAD", 0),
                        template("t3", "Site audit", "REPORTING", 0)),
                List.of());

        Finding dead = findingOf(s6.analyse(snapshot), "never_matched_template");

        assertThat(dead.headline()).contains("2 of 3");
        assertThat(dead.evidence().get(Finding.EvidenceKind.TEMPLATE)).containsExactly("t2", "t3");
        assertThat(dead.action()).isEqualTo("Review the library");
    }

    @Test
    void theDeadCountStatesTheLibraryItIsOutOf() {
        Snapshot snapshot = of(
                List.of(
                        template("t1", "Caption set", "CONTENT", 7),
                        template("t2", "Quarterly retro", "TEAM_LEAD", 0),
                        template("t3", "Site audit", "REPORTING", 0),
                        template("t4", "Ad variant", "ADS", 0)),
                List.of());

        Finding dead = findingOf(s6.analyse(snapshot), "never_matched_template");

        assertThat(dead.reach()).isEqualTo(3);
        assertThat(dead.reachOf()).isEqualTo(4);
        assertThat(dead.share()).hasValue(0.75);
    }

    @Test
    void aLibraryWhereEveryTemplateHasMatchedIsReportedAsCleanRatherThanAsSilence() {
        Snapshot snapshot = of(List.of(template("t1", "Caption set", "CONTENT", 7)), List.of(bracket("b1", "CONTENT")));

        Report report = s6.analyse(snapshot);

        assertThat(report.findings()).isEmpty();
        assertThat(report.clean()).extracting(Clean::what).contains("every_template_matched");
        assertThat(report.saysSomething()).isTrue();
    }

    @Test
    void workBeingDoneWithNoTemplateForItIsCountedAndNamed() {
        Snapshot snapshot = of(
                List.of(template("t1", "Caption set", "CONTENT", 7)),
                List.of(bracket("b1", "CONTENT"), bracket("b2", "ADS"), bracket("b3", "PHOTO")));

        Finding missing = findingOf(s6.analyse(snapshot), "work_kind_without_a_template");

        assertThat(missing.headline()).contains("2 of 3");
        assertThat(missing.because().getFirst()).contains("ADS").contains("PHOTO");
        assertThat(missing.reach()).isEqualTo(2);
        assertThat(missing.reachOf()).isEqualTo(3);
        assertThat(missing.action()).isEqualTo("Write a template");
    }

    @Test
    void aDeadTemplateIsDeadHereAndCoversNothingInS9() {
        Snapshot.Template dead = template("t1", "Caption set", "CONTENT", 0);

        assertThat(dead.hasMatchedWork()).isFalse();

        Report report = s6.analyse(of(List.of(dead), List.of(bracket("b1", "CONTENT"))));

        assertThat(findingOf(report, "never_matched_template").reach()).isEqualTo(1);

        assertThat(report.findings()).extracting(Finding::kind).doesNotContain("work_kind_without_a_template");
    }

    @Test
    void aLongListOfDeadTemplatesNamesTheFirstFewAndCountsTheRest() {
        List<Snapshot.Template> many = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            many.add(template("t" + index, "Template " + index, "CONTENT", 0));
        }

        Finding dead = findingOf(s6.analyse(of(many, List.of())), "never_matched_template");

        assertThat(dead.because().getFirst()).contains("and 3 more");

        assertThat(dead.evidence().get(Finding.EvidenceKind.TEMPLATE)).hasSize(8);
    }

    @Test
    void itAlwaysSaysSomething() {
        assertThat(s6.analyse(of(List.of(), List.of())).saysSomething()).isTrue();
        assertThat(s6.analyse(of(List.of(template("t1", "Caption set", "CONTENT", 7)), List.of()))
                        .saysSomething())
                .isTrue();
        assertThat(s6.analyse(of(List.of(template("t1", "Caption set", "CONTENT", 0)), List.of()))
                        .saysSomething())
                .isTrue();
    }

    @Test
    void itSaysWhatItFoundAndRetiresNothing() {
        Finding dead = findingOf(
                s6.analyse(of(List.of(template("t1", "Caption set", "CONTENT", 0)), List.of())),
                "never_matched_template");

        assertThat(dead.because()).anyMatch(line -> line.contains("Nothing here has been retired"));
    }
}
