package com.flowops.analyser.application.analysers;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.analyser.domain.Absence;
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
class EffortAnalyserTest {
    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-01T00:00:00Z");

    private final EffortAnalyser s3 = new EffortAnalyser();

    private static Snapshot.Bracket bracket(String id, String closeKind, long hours) {
        return new Snapshot.Bracket(
                id,
                "job-1",
                "CONTENT",
                closeKind,
                "LINK",
                FROM,
                closeKind == null ? null : FROM.plusSeconds(hours * 3600));
    }

    private static Snapshot.Node node(String id, List<Snapshot.Move> moves) {
        return new Snapshot.Node(
                id, "job-1", "CONTENT", "caption set", null, "text", "CLOSED", "TEXT", "WRITER", FROM, TO, moves);
    }

    private static Snapshot.Move move(String from, String to) {
        return new Snapshot.Move(from, to, "person-1", FROM);
    }

    private static Snapshot of(List<Snapshot.Bracket> brackets, List<Snapshot.Node> nodes) {
        return new Snapshot(FROM, TO, nodes, brackets, List.of(), List.of(), List.of(), Snapshot.Shapes.none());
    }

    private static Snapshot.Bracket bracketOfMinutes(String id, long minutes) {
        return new Snapshot.Bracket(id, "job-1", "CONTENT", "DELIVERED", "LINK", FROM, FROM.plusSeconds(minutes * 60));
    }

    private static List<Snapshot.Bracket> finishedTakingMinutes(long... minutes) {
        List<Snapshot.Bracket> brackets = new ArrayList<>();
        for (int index = 0; index < minutes.length; index++) {
            brackets.add(bracketOfMinutes("m" + index, minutes[index]));
        }
        return brackets;
    }

    private static List<Snapshot.Bracket> finishedTaking(long... hours) {
        List<Snapshot.Bracket> brackets = new ArrayList<>();
        for (int index = 0; index < hours.length; index++) {
            brackets.add(bracket("b" + index, "DELIVERED", hours[index]));
        }
        return brackets;
    }

    private static Finding findingOf(Report report, String kind) {
        return report.findings().stream()
                .filter(finding -> finding.kind().equals(kind))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + kind + " in " + report.findings()));
    }

    @Test
    void tooLittleFinishedWorkIsAStatedPreconditionRatherThanAMedianOfTwo() {
        Report report = s3.analyse(of(finishedTaking(4, 6), List.of()));

        assertThat(report.findings()).isEmpty();
        assertThat(report.wasBlocked()).isTrue();
        assertThat(report.preconditions().getFirst().had()).contains("2 of 2");
        assertThat(report.saysSomething()).isTrue();
    }

    @Test
    void theMedianIsTakenOverFinishedWorkOnly() {
        Finding median = findingOf(s3.analyse(of(finishedTaking(2, 6, 10), List.of())), "median_time_to_finish");

        assertThat(median.headline()).contains("6 hours");
        assertThat(median.reach()).isEqualTo(3);
        assertThat(median.reachOf()).isEqualTo(3);
    }

    @Test
    void workClosedBecauseItsEngagementEndedIsNotInTheMedian() {
        List<Snapshot.Bracket> brackets = new ArrayList<>(finishedTaking(10, 12, 14));
        brackets.add(bracket("job-end-1", "JOB_END", 1));
        brackets.add(bracket("cadence-1", "CADENCE_CLOSED", 1));

        Finding median = findingOf(s3.analyse(of(brackets, List.of())), "median_time_to_finish");

        assertThat(median.headline()).contains("12 hours");
        assertThat(median.reach()).isEqualTo(3);

        assertThat(median.reachOf()).isEqualTo(5);
    }

    @Test
    void theStatesNotInTheMedianAreCountedWhereTheReaderSeesThem() {
        List<Snapshot.Bracket> brackets = new ArrayList<>(finishedTaking(10, 12, 14));
        brackets.add(bracket("open-1", null, 0));
        brackets.add(bracket("dropped-1", "DROPPED", 3));
        brackets.add(bracket("job-end-1", "JOB_END", 1));
        brackets.add(bracket("handed-1", "HANDED_OVER", 5));

        Finding median = findingOf(s3.analyse(of(brackets, List.of())), "median_time_to_finish");

        assertThat(median.because())
                .anyMatch(line -> line.contains("1 still open")
                        && line.contains("1 abandoned")
                        && line.contains("1 closed because")
                        && line.contains("1 handed over"));
    }

    @Test
    void workThatStalledIsCountedFromItsHistory() {
        List<Snapshot.Node> nodes = List.of(
                node("n1", List.of(move(null, "MARKED"), move("IN_PROGRESS", "BLOCKED"))),
                node("n2", List.of(move(null, "MARKED"))));

        Finding stalled = findingOf(s3.analyse(of(finishedTaking(4, 6, 8), nodes)), "work_that_stalled");

        assertThat(stalled.headline()).contains("1 of 2");
        assertThat(stalled.evidence().get(Finding.EvidenceKind.NODE)).containsExactly("n1");
    }

    @Test
    void workThatCameBackIsCountedFromASecondPassThroughInProgress() {
        List<Snapshot.Node> nodes = List.of(
                node(
                        "n1",
                        List.of(
                                move(null, "MARKED"),
                                move("ASSIGNED", "IN_PROGRESS"),
                                move("IN_PROGRESS", "COMPLETED"),
                                move("COMPLETED", "IN_PROGRESS"))),
                node("n2", List.of(move(null, "MARKED"), move("ASSIGNED", "IN_PROGRESS"))));

        Finding rework = findingOf(s3.analyse(of(finishedTaking(4, 6, 8), nodes)), "work_that_came_back");

        assertThat(rework.reach()).isEqualTo(1);
        assertThat(rework.reachOf()).isEqualTo(2);
    }

    @Test
    void workHandedBackIsCountedAndFramedAsStaffing() {
        List<Snapshot.Node> nodes = List.of(node("n1", List.of(move(null, "MARKED"), move("ASSIGNED", "BOUNCED"))));

        Finding handed = findingOf(s3.analyse(of(finishedTaking(4, 6, 8), nodes)), "work_that_changed_hands");

        assertThat(handed.because()).anyMatch(line -> line.contains("staffing fact"));
        assertThat(handed.action()).isEqualTo("Look at how work is assigned");
    }

    @Test
    void workMarkedBeforeItsHistoryWasRecordedIsExcludedAndSaidSoOutLoud() {
        List<Snapshot.Node> nodes = List.of(
                node("trailed", List.of(move(null, "MARKED"), move("IN_PROGRESS", "BLOCKED"))),
                node("older", List.of()),
                node("older-2", List.of()));

        Report report = s3.analyse(of(finishedTaking(4, 6, 8), nodes));

        assertThat(report.absences()).extracting(Absence::what).contains("no_trail");
        assertThat(report.absences().stream()
                        .filter(a -> a.what().equals("no_trail"))
                        .findFirst()
                        .orElseThrow()
                        .detail())
                .contains("2 of 3");

        assertThat(findingOf(report, "work_that_stalled").reachOf()).isEqualTo(1);
    }

    @Test
    void withNoRecordedHistoryTheTrailQuestionsAreBlockedRatherThanAnsweredWithZero() {
        Report report = s3.analyse(of(finishedTaking(4, 6, 8), List.of(node("older", List.of()))));

        assertThat(report.absences())
                .filteredOn(absence -> absence.what().equals("no_trail"))
                .anyMatch(Absence::blocking);
        assertThat(report.findings()).extracting(Finding::kind).doesNotContain("work_that_stalled");
    }

    @Test
    void aWindowWhereNothingStalledIsReportedAsClean() {
        List<Snapshot.Node> nodes = List.of(node("n1", List.of(move(null, "MARKED"))));

        Report report = s3.analyse(of(finishedTaking(4, 6, 8), nodes));

        assertThat(report.clean()).extracting(Clean::what).contains("nothing_stalled");
    }

    @Test
    void itAlwaysSaysSomething() {
        assertThat(s3.analyse(of(List.of(), List.of())).saysSomething()).isTrue();
        assertThat(s3.analyse(of(finishedTaking(4, 6, 8), List.of())).saysSomething())
                .isTrue();
    }

    @Test
    void workThatTakesUnderAnHourIsReportedInMinutesRatherThanAsZeroHours() {
        Finding median =
                findingOf(s3.analyse(of(finishedTakingMinutes(20, 40, 55), List.of())), "median_time_to_finish");

        assertThat(median.headline()).isEqualTo("Work that finishes takes about 40 minutes");
        assertThat(median.headline()).doesNotContain("0 hours");
    }

    @Test
    void workThatTakesDaysIsReportedInHoursRatherThanInMinutes() {
        Finding median =
                findingOf(s3.analyse(of(finishedTakingMinutes(300, 460, 600), List.of())), "median_time_to_finish");

        assertThat(median.headline()).isEqualTo("Work that finishes takes about 7 hours");
    }

    @Test
    void oneOfEitherUnitIsSpeltSingular() {
        assertThat(findingOf(s3.analyse(of(finishedTakingMinutes(1, 1, 1), List.of())), "median_time_to_finish")
                        .headline())
                .isEqualTo("Work that finishes takes about 1 minute");
        assertThat(findingOf(s3.analyse(of(finishedTakingMinutes(120, 125, 130), List.of())), "median_time_to_finish")
                        .headline())
                .isEqualTo("Work that finishes takes about 2 hours");
    }

    @Test
    void aMedianThatRoundsToNothingIsWithheldRatherThanReportedAsZero() {
        Report report = s3.analyse(of(finishedTakingMinutes(0, 0, 0), List.of()));

        assertThat(report.findings()).extracting(Finding::kind).doesNotContain("median_time_to_finish");
        assertThat(report.saysSomething()).isTrue();
    }
}
