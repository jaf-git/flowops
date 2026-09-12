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
class JobShapeAnalyserTest {
    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-01T00:00:00Z");

    private final JobShapeAnalyser s5 = new JobShapeAnalyser();

    private static Snapshot.Job job(String id, boolean closed) {
        return new Snapshot.Job(
                id, "Engagement " + id, closed ? "CLOSED" : "OPEN", null, null, FROM, closed ? TO : null);
    }

    private static Snapshot.Bracket bracket(String id, String jobId, String closeKind) {
        return new Snapshot.Bracket(id, jobId, "CONTENT", closeKind, "LINK", FROM, closeKind == null ? null : TO);
    }

    private static Snapshot of(List<Snapshot.Job> jobs, List<Snapshot.Bracket> brackets) {
        return new Snapshot(FROM, TO, List.of(), brackets, jobs, List.of(), List.of(), Snapshot.Shapes.none());
    }

    private static void ordinary(List<Snapshot.Job> jobs, List<Snapshot.Bracket> brackets, int count) {
        for (int index = 0; index < count; index++) {
            String id = "ok-" + index;
            jobs.add(job(id, true));
            brackets.add(bracket("b-" + id, id, "DELIVERED"));
        }
    }

    private static Finding findingOf(Report report, String kind) {
        return report.findings().stream()
                .filter(finding -> finding.kind().equals(kind))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + kind + " in " + report.findings()));
    }

    @Test
    void withTooFewEngagementsThereIsNothingToCompareAndItSaysSo() {
        Report report = s5.analyse(of(List.of(job("j1", false), job("j2", false)), List.of()));

        assertThat(report.findings()).isEmpty();
        assertThat(report.wasBlocked()).isTrue();
        assertThat(report.saysSomething()).isTrue();
    }

    @Test
    void aWorkspaceWhereEveryEngagementLooksOrdinarySaysSoOnce() {
        List<Snapshot.Job> jobs = new ArrayList<>();
        List<Snapshot.Bracket> brackets = new ArrayList<>();
        ordinary(jobs, brackets, 6);

        Report report = s5.analyse(of(jobs, brackets));

        assertThat(report.findings()).isEmpty();
        assertThat(report.clean()).extracting(Clean::what).contains("every_engagement_looks_ordinary");
    }

    @Test
    void anOpenEngagementWithNothingFinishedInItIsStalled() {
        List<Snapshot.Job> jobs = new ArrayList<>();
        List<Snapshot.Bracket> brackets = new ArrayList<>();
        ordinary(jobs, brackets, 5);

        jobs.add(job("stuck", false));
        brackets.add(bracket("b-stuck-1", "stuck", null));
        brackets.add(bracket("b-stuck-2", "stuck", null));

        Finding stalled = findingOf(s5.analyse(of(jobs, brackets)), "engagement_producing_nothing");

        assertThat(stalled.reach()).isEqualTo(1);
        assertThat(stalled.reachOf()).isEqualTo(6);
        assertThat(stalled.evidence().get(Finding.EvidenceKind.JOB)).containsExactly("stuck");
    }

    @Test
    void anOpenEngagementThatIsStillDeliveringIsNotStalled() {
        List<Snapshot.Job> jobs = new ArrayList<>();
        List<Snapshot.Bracket> brackets = new ArrayList<>();
        ordinary(jobs, brackets, 5);

        jobs.add(job("running", false));
        brackets.add(bracket("b-running-1", "running", "DELIVERED"));
        brackets.add(bracket("b-running-2", "running", null));

        Report report = s5.analyse(of(jobs, brackets));

        assertThat(report.findings()).extracting(Finding::kind).doesNotContain("engagement_producing_nothing");
    }

    @Test
    void anEngagementSeveralTimesTheUsualSizeIsWorthASecondLook() {
        List<Snapshot.Job> jobs = new ArrayList<>();
        List<Snapshot.Bracket> brackets = new ArrayList<>();
        ordinary(jobs, brackets, 6);

        jobs.add(job("huge", true));
        for (int index = 0; index < 9; index++) {
            brackets.add(bracket("b-huge-" + index, "huge", "DELIVERED"));
        }

        Finding large = findingOf(s5.analyse(of(jobs, brackets)), "engagement_much_larger_than_usual");

        assertThat(large.because()).anyMatch(line -> line.contains("this workspace's own median"));
        assertThat(large.evidence().get(Finding.EvidenceKind.JOB)).containsExactly("huge");
    }

    @Test
    void aWorkspaceWhereEveryEngagementIsLargeReportsNoneOfThemAsLarge() {
        List<Snapshot.Job> jobs = new ArrayList<>();
        List<Snapshot.Bracket> brackets = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            String id = "big-" + index;
            jobs.add(job(id, true));
            for (int piece = 0; piece < 9; piece++) {
                brackets.add(bracket("b-" + id + "-" + piece, id, "DELIVERED"));
            }
        }

        Report report = s5.analyse(of(jobs, brackets));

        assertThat(report.findings()).extracting(Finding::kind).doesNotContain("engagement_much_larger_than_usual");
    }

    @Test
    void engagementsWithNoRecordedWorkAreDeclaredRatherThanCountedAsOrdinary() {
        List<Snapshot.Job> jobs = new ArrayList<>();
        List<Snapshot.Bracket> brackets = new ArrayList<>();
        ordinary(jobs, brackets, 5);
        jobs.add(job("empty", false));

        Report report = s5.analyse(of(jobs, brackets));

        assertThat(report.absences()).extracting(Absence::what).contains("engagement_with_no_recorded_work");

        assertThat(report.findings()).extracting(Finding::kind).doesNotContain("engagement_producing_nothing");
    }

    @Test
    void itAlwaysSaysSomething() {
        assertThat(s5.analyse(of(List.of(), List.of())).saysSomething()).isTrue();

        List<Snapshot.Job> jobs = new ArrayList<>();
        List<Snapshot.Bracket> brackets = new ArrayList<>();
        ordinary(jobs, brackets, 6);
        assertThat(s5.analyse(of(jobs, brackets)).saysSomething()).isTrue();
    }
}
