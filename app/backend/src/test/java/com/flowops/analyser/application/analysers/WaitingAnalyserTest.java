package com.flowops.analyser.application.analysers;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.analyser.domain.Absence;
import com.flowops.analyser.domain.Clean;
import com.flowops.analyser.domain.Finding;
import com.flowops.analyser.domain.Report;
import com.flowops.analyser.domain.Severity;
import com.flowops.analyser.domain.Snapshot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ANALYSER-RUN-01")
class WaitingAnalyserTest {
    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-01T00:00:00Z");

    private final WaitingAnalyser s4 = new WaitingAnalyser();

    private static Snapshot.Wait answered(String id, String waitingOn, long days) {
        return new Snapshot.Wait(id, "bracket-1", waitingOn, FROM, FROM.plusSeconds(days * 86400), null);
    }

    private static Snapshot.Wait cancelled(String id, String waitingOn) {
        return new Snapshot.Wait(id, "bracket-1", waitingOn, FROM, null, FROM.plusSeconds(86400));
    }

    private static Snapshot.Wait stillWaiting(String id, String waitingOn) {
        return new Snapshot.Wait(id, "bracket-1", waitingOn, FROM, null, null);
    }

    private static Snapshot of(List<Snapshot.Wait> waits) {
        return new Snapshot(FROM, TO, List.of(), List.of(), List.of(), waits, List.of(), Snapshot.Shapes.none());
    }

    private static Finding findingOf(Report report, String kind) {
        return report.findings().stream()
                .filter(finding -> finding.kind().equals(kind))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + kind + " in " + report.findings()));
    }

    @Test
    void withNoRecordedWaitsTheAnswerIsAStatedPrecondition() {
        Report report = s4.analyse(of(List.of()));

        assertThat(report.findings()).isEmpty();
        assertThat(report.wasBlocked()).isTrue();
        assertThat(report.saysSomething()).isTrue();
    }

    @Test
    void waitsNobodyEverAnsweredAreTheHeadline() {
        Report report = s4.analyse(of(List.of(
                cancelled("w1", "CLIENT"),
                cancelled("w2", "SUPPLIER"),
                stillWaiting("w3", "CLIENT"),
                answered("w4", "COLLEAGUE", 2))));

        Finding unanswered = findingOf(report, "wait_never_satisfied");

        assertThat(unanswered.headline()).contains("3 of 4");
        assertThat(unanswered.reach()).isEqualTo(3);
        assertThat(unanswered.reachOf()).isEqualTo(4);
        assertThat(unanswered.evidence().get(Finding.EvidenceKind.WAIT)).containsExactly("w1", "w2", "w3");
    }

    @Test
    void anUnansweredExternalWaitIsRankedAboveAnInternalOne() {
        Severity external = findingOf(s4.analyse(of(List.of(cancelled("w1", "CLIENT")))), "wait_never_satisfied")
                .severity();
        Severity internal = findingOf(s4.analyse(of(List.of(cancelled("w1", "COLLEAGUE")))), "wait_never_satisfied")
                .severity();

        assertThat(external).isEqualTo(Severity.HIGH);
        assertThat(internal).isEqualTo(Severity.MEDIUM);
    }

    @Test
    void aClientsSilenceAndAColleaguesAreReportedApartAndNeverAddedTogether() {
        Report report = s4.analyse(of(List.of(
                answered("c1", "CLIENT", 10),
                answered("c2", "CLIENT", 10),
                answered("k1", "COLLEAGUE", 2),
                answered("k2", "COLLEAGUE", 2))));

        Finding client = findingOf(report, "days_waiting_on_client");
        Finding colleague = findingOf(report, "days_waiting_on_colleague");

        assertThat(client.headline()).contains("10 days");
        assertThat(colleague.headline()).contains("2 days");

        assertThat(report.findings()).extracting(Finding::kind).doesNotContain("days_waiting");
    }

    @Test
    void anExternalWaitSaysItIsNotAFigureAboutAnybodyHere() {
        Finding client = findingOf(s4.analyse(of(List.of(answered("c1", "CLIENT", 4)))), "days_waiting_on_client");

        assertThat(client.because()).anyMatch(line -> line.contains("invariant I4"));
    }

    @Test
    void aKindWhereNothingWasEverAnsweredReportsAnAbsenceRatherThanALength() {
        Report report = s4.analyse(of(List.of(cancelled("w1", "CLIENT"), stillWaiting("w2", "CLIENT"))));

        assertThat(report.findings()).extracting(Finding::kind).doesNotContain("days_waiting_on_client");
        assertThat(report.absences()).extracting(Absence::what).contains("no_satisfied_wait_of_kind_client");
    }

    @Test
    void aThinSetOfWaitsStillAnswersAndSaysHowThinItIs() {
        Report report = s4.analyse(of(List.of(answered("c1", "CLIENT", 4), cancelled("c2", "CLIENT"))));

        assertThat(report.preconditions().getFirst().met()).isFalse();
        assertThat(report.preconditions().getFirst().had()).contains("2 recorded");
        assertThat(report.findings()).isNotEmpty();
    }

    @Test
    void aWindowWhereEverybodyAnsweredIsReportedAsClean() {
        Report report = s4.analyse(of(List.of(answered("c1", "CLIENT", 3), answered("k1", "COLLEAGUE", 1))));

        assertThat(report.findings()).extracting(Finding::kind).doesNotContain("wait_never_satisfied");
        assertThat(report.clean()).extracting(Clean::what).contains("every_wait_resolved");
    }

    @Test
    void itAlwaysSaysSomething() {
        assertThat(s4.analyse(of(List.of())).saysSomething()).isTrue();
        assertThat(s4.analyse(of(List.of(answered("c1", "CLIENT", 3)))).saysSomething())
                .isTrue();
        assertThat(s4.analyse(of(List.of(cancelled("c1", "CLIENT")))).saysSomething())
                .isTrue();
    }
}
