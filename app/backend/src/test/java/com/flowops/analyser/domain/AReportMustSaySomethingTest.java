package com.flowops.analyser.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ANALYSER-RUN-01")
class AReportMustSaySomethingTest {
    private static final Clean EVERY_TEMPLATE_MATCHED = new Clean(
            "every_template_matched",
            "18 approved templates checked; every one has matched work and 89% of what you do is covered.");

    private static final Absence NO_WORK_TYPE =
            new Absence("no_work_type", "212 of 348 nodes have no work type.", false);

    private static Finding aDeadTemplate() {
        return new Finding(
                "S6_LIBRARY",
                "dead_template",
                SubjectKind.WORK_TYPE,
                "CONTENT",
                Category.YOUR_LIBRARY,
                "headline",
                List.of("because"),
                Map.of(),
                Severity.LOW,
                Confidence.HIGH,
                1,
                null,
                "act");
    }

    private static Report reportWith(List<Finding> findings, List<Absence> absences, List<Clean> clean) {
        return new Report("S6_LIBRARY", 348, findings, absences, clean, List.of());
    }

    @Test
    void aReportWithNothingInAnyOfItsThreeVoicesSaysNothing() {
        Report silent = reportWith(List.of(), List.of(), List.of());

        assertThat(silent.saysSomething()).isFalse();
        assertThat(silent.wasBlocked()).isFalse();
    }

    @Test
    void aMetPreconditionAloneDoesNotCountAsSayingSomething() {
        Report ready = new Report(
                "S6_LIBRARY",
                348,
                List.of(),
                List.of(),
                List.of(),
                List.of(Precondition.met("at least 3 finished engagements", "you have 7")));

        assertThat(ready.saysSomething()).isFalse();
        assertThat(ready.wasBlocked()).isFalse();
    }

    @Test
    void aFindingOnItsOwnSaysSomething() {
        assertThat(reportWith(List.of(aDeadTemplate()), List.of(), List.of()).saysSomething())
                .isTrue();
    }

    @Test
    void anAbsenceOnItsOwnSaysSomething() {
        assertThat(reportWith(List.of(), List.of(NO_WORK_TYPE), List.of()).saysSomething())
                .isTrue();
    }

    @Test
    void aCleanResultOnItsOwnSaysSomething() {
        assertThat(reportWith(List.of(), List.of(), List.of(EVERY_TEMPLATE_MATCHED))
                        .saysSomething())
                .isTrue();
    }

    @Test
    void aCleanReportCarriesItsGoodNewsAndNothingElse() {
        Report report = Report.clean("S6_LIBRARY", 348, EVERY_TEMPLATE_MATCHED);

        assertThat(report.analyser()).isEqualTo("S6_LIBRARY");
        assertThat(report.read()).isEqualTo(348);
        assertThat(report.clean()).containsExactly(EVERY_TEMPLATE_MATCHED);
        assertThat(report.findings()).isEmpty();
        assertThat(report.absences()).isEmpty();
        assertThat(report.preconditions()).isEmpty();
        assertThat(report.saysSomething()).isTrue();
        assertThat(report.wasBlocked()).isFalse();
    }

    @Test
    void aBlockedReportSaysSomethingBecauseTheUnmetPreconditionIsTheOutput() {
        Precondition unmet = Precondition.unmet("at least 3 finished engagements", "you have 1", "Close an engagement");
        Report report = Report.blocked("S6_LIBRARY", 12, unmet);

        assertThat(report.preconditions()).containsExactly(unmet);
        assertThat(report.findings()).isEmpty();
        assertThat(report.absences()).isEmpty();
        assertThat(report.clean()).isEmpty();
        assertThat(report.read()).isEqualTo(12);
        assertThat(report.saysSomething()).isTrue();
        assertThat(report.wasBlocked()).isTrue();
    }

    @Test
    void listsNobodyFilledInBecomeEmptyRatherThanNull() {
        Report report = new Report("S6_LIBRARY", 0, null, null, null, null);

        assertThat(report.findings()).isEmpty();
        assertThat(report.absences()).isEmpty();
        assertThat(report.clean()).isEmpty();
        assertThat(report.preconditions()).isEmpty();
        assertThat(report.saysSomething()).isFalse();
        assertThat(report.wasBlocked()).isFalse();
    }

    @Test
    void anAnalyserCannotHaveReadFewerThanNoThings() {
        assertThatThrownBy(() -> new Report("S6_LIBRARY", -1, List.of(), List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aCleanResultWithoutACodeOrASentenceIsRefused() {
        assertThatThrownBy(() -> new Clean(null, "detail")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Clean("every_template_matched", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void aBlockingAbsenceBlocksJustAsAnUnmetPreconditionDoes() {
        Absence blocking = new Absence("no_titles_at_all", "0 of 356 nodes carry a title.", true);

        assertThat(reportWith(List.of(), List.of(blocking), List.of()).wasBlocked())
                .isTrue();
    }

    @Test
    void anAbsenceThatMerelyNarrowedTheAnswerDoesNotBlock() {
        Report narrowed = new Report(
                "S6_LIBRARY",
                348,
                List.of(aDeadTemplate()),
                List.of(NO_WORK_TYPE),
                List.of(),
                List.of(Precondition.met("a snapshot", "one snapshot, shared")));

        assertThat(narrowed.saysSomething()).isTrue();
        assertThat(narrowed.wasBlocked()).isFalse();
    }
}
