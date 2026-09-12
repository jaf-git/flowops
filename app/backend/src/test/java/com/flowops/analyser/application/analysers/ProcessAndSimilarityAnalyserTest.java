package com.flowops.analyser.application.analysers;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.analyser.domain.Absence;
import com.flowops.analyser.domain.Clean;
import com.flowops.analyser.domain.Finding;
import com.flowops.analyser.domain.Report;
import com.flowops.analyser.domain.Snapshot;
import com.flowops.analyser.domain.SubjectKind;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ANALYSER-RUN-01")
class ProcessAndSimilarityAnalyserTest {
    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-01T00:00:00Z");

    private final ProcessAnalyser s8 = new ProcessAnalyser();
    private final SimilarityAnalyser s7 = new SimilarityAnalyser();

    private static Snapshot.Shape kind(String id, String workType, List<String> jobIds, boolean subprocess) {
        return new Snapshot.Shape(id, workType, subprocess, List.of(id + "-n1", id + "-n2"), jobIds, 0.9, 0.9);
    }

    private static Snapshot.Process process(List<String> steps, int runs, boolean orderReliable) {
        return new Snapshot.Process(
                steps,
                orderReliable ? steps : steps.stream().sorted().toList(),
                orderReliable,
                orderReliable ? 0.9 : 0.4,
                List.of("job-1", "job-2"),
                runs,
                0.85);
    }

    private static Snapshot with(Snapshot.Shapes shapes) {
        return new Snapshot(FROM, TO, List.of(), List.of(), List.of(), List.of(), List.of(), shapes);
    }

    private static Finding findingOf(Report report, String kind) {
        return report.findings().stream()
                .filter(finding -> finding.kind().equals(kind))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + kind + " in " + report.findings()));
    }

    @Test
    void whenTheClustererNeverRanS8SaysSoRatherThanReportingNoProcesses() {
        Report report = s8.analyse(with(Snapshot.Shapes.none()));

        assertThat(report.findings()).isEmpty();
        assertThat(report.wasBlocked()).isTrue();
        assertThat(report.saysSomething()).isTrue();
    }

    @Test
    void aRepeatedProcessIsReportedWithItsEvidence() {
        Snapshot.Shapes shapes = new Snapshot.Shapes(
                40,
                0,
                List.of(kind("K:CONTENT", "CONTENT", List.of("job-1", "job-2"), false)),
                List.of(process(List.of("K:CONTENT", "K:ADS", "K:REPORTING"), 7, true)));

        Finding found = findingOf(s8.analyse(with(shapes)), "repeated_process");

        assertThat(found.headline()).contains("3-step process").contains("7 times");
        assertThat(found.subjectKind()).isEqualTo(SubjectKind.SHAPE);
        assertThat(found.evidence().get(Finding.EvidenceKind.JOB)).containsExactly("job-1", "job-2");
        assertThat(found.action()).isEqualTo("Write it down as a process");
    }

    @Test
    void aProcessRunCountCarriesNoDenominator() {
        Snapshot.Shapes shapes =
                new Snapshot.Shapes(40, 0, List.of(), List.of(process(List.of("K:A", "K:B", "K:C"), 7, true)));

        Finding found = findingOf(s8.analyse(with(shapes)), "repeated_process");

        assertThat(found.reach()).isEqualTo(7);
        assertThat(found.reachOf()).isNull();
        assertThat(found.share()).isEmpty();
    }

    @Test
    void anUnreliableOrderIsDescribedAsNotShownRatherThanPrinted() {
        Snapshot.Shapes shapes =
                new Snapshot.Shapes(40, 0, List.of(), List.of(process(List.of("K:A", "K:B", "K:C"), 3, false)));

        Finding found = findingOf(s8.analyse(with(shapes)), "repeated_process");

        assertThat(found.because()).anyMatch(line -> line.contains("The order is not shown"));
        assertThat(found.because()).noneMatch(line -> line.contains("→"));
    }

    @Test
    void clusteredWithNoRepeatedProcessIsReportedRatherThanLeftEmpty() {
        Snapshot.Shapes shapes =
                new Snapshot.Shapes(40, 0, List.of(kind("K:CONTENT", "CONTENT", List.of("job-1"), false)), List.of());

        Report report = s8.analyse(with(shapes));

        assertThat(report.findings()).isEmpty();
        assertThat(report.clean()).extracting(Clean::what).contains("no_repeated_process");
        assertThat(report.saysSomething()).isTrue();
    }

    @Test
    void s8AlwaysStatesWhatItStructurallyCannotSee() {
        Snapshot.Shapes withProcess =
                new Snapshot.Shapes(40, 0, List.of(), List.of(process(List.of("K:A", "K:B", "K:C"), 7, true)));
        Snapshot.Shapes withoutProcess = new Snapshot.Shapes(40, 0, List.of(), List.of());

        for (Snapshot.Shapes shapes : List.of(withProcess, withoutProcess)) {
            assertThat(s8.analyse(with(shapes)).absences())
                    .extracting(Absence::what)
                    .contains("intra_department_process");
        }
    }

    @Test
    void whatChurnRemovedBeforeClusteringIsDeclared() {
        Snapshot.Shapes shapes = new Snapshot.Shapes(40, 12, List.of(), List.of());

        assertThat(s8.analyse(with(shapes)).absences())
                .extracting(Absence::what)
                .contains("removed_as_churn");
    }

    @Test
    void workThatRecursAcrossEngagementsIsReported() {
        Snapshot.Shapes shapes = new Snapshot.Shapes(
                40,
                0,
                List.of(
                        kind("K:CONTENT", "CONTENT", List.of("job-1", "job-2"), false),
                        kind("K:ADS", "ADS", List.of("job-1"), false)),
                List.of());

        Finding shared = findingOf(s7.analyse(with(shapes)), "work_shared_across_engagements");

        assertThat(shared.reach()).isEqualTo(1);
        assertThat(shared.reachOf()).isEqualTo(2);
        assertThat(shared.because().getFirst()).contains("CONTENT").contains("2 engagements");
    }

    @Test
    void whenNoShapeCrossesAnEngagementS7SaysWhyS8FoundNothing() {
        Snapshot.Shapes shapes = new Snapshot.Shapes(
                40,
                0,
                List.of(
                        kind("K:CONTENT", "CONTENT", List.of("job-1"), false),
                        kind("K:ADS", "ADS", List.of("job-2"), false)),
                List.of());

        Report s7Report = s7.analyse(with(shapes));
        Report s8Report = s8.analyse(with(shapes));

        assertThat(s7Report.absences())
                .filteredOn(absence -> absence.what().equals("no_shape_crosses_an_engagement"))
                .anyMatch(Absence::blocking);

        assertThat(s8Report.findings()).isEmpty();
    }

    @Test
    void workOrganisedIntoSideConversationsIsReportedAsGoodNews() {
        Snapshot.Shapes shapes = new Snapshot.Shapes(
                40,
                0,
                List.of(
                        kind("K:WRITER/dm-sk", "WRITER", List.of("job-1", "job-2"), true),
                        kind("K:CONTENT", "CONTENT", List.of("job-1", "job-2"), false)),
                List.of());

        assertThat(s7.analyse(with(shapes)).clean())
                .extracting(Clean::what)
                .contains("work_organised_into_side_conversations");
    }

    @Test
    void bothAlwaysSaySomething() {
        Snapshot.Shapes nothing = Snapshot.Shapes.none();
        Snapshot.Shapes clustered = new Snapshot.Shapes(40, 0, List.of(), List.of());

        for (Snapshot.Shapes shapes : List.of(nothing, clustered)) {
            assertThat(s7.analyse(with(shapes)).saysSomething()).isTrue();
            assertThat(s8.analyse(with(shapes)).saysSomething()).isTrue();
        }
    }
}
