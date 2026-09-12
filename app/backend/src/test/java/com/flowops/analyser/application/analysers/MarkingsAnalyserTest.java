package com.flowops.analyser.application.analysers;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.analyser.domain.Absence;
import com.flowops.analyser.domain.Clean;
import com.flowops.analyser.domain.Finding;
import com.flowops.analyser.domain.Report;
import com.flowops.analyser.domain.Snapshot;
import com.flowops.analyser.domain.SubjectKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ANALYSER-RUN-01")
class MarkingsAnalyserTest {
    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-01T00:00:00Z");

    private final MarkingsAnalyser s1 = new MarkingsAnalyser();

    private static Snapshot.Node mark(String id, String workType, String text) {
        return new Snapshot.Node(
                id, "job-1", workType, null, null, text, "CLOSED", "TEXT", "WRITER", FROM, TO, List.of());
    }

    private static Snapshot of(List<Snapshot.Node> marks) {
        return new Snapshot(FROM, TO, marks, List.of(), List.of(), List.of(), List.of(), Snapshot.Shapes.none());
    }

    private static String realWork() {
        return "wrote the caption set for the Aurora summer menu launch";
    }

    private static List<Snapshot.Node> good(String workType, int count) {
        List<Snapshot.Node> marks = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            marks.add(mark(workType + "-good-" + index, workType, realWork()));
        }
        return marks;
    }

    private static Finding findingOf(Report report, String kind) {
        return report.findings().stream()
                .filter(finding -> finding.kind().equals(kind))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + kind + " in " + report.findings()));
    }

    @Test
    void withNothingMarkedTheAnswerIsAStatedPrecondition() {
        Report report = s1.analyse(of(List.of()));

        assertThat(report.findings()).isEmpty();
        assertThat(report.wasBlocked()).isTrue();
        assertThat(report.saysSomething()).isTrue();
    }

    @Test
    void aMarkThatSaysWhatTheWorkWasIsNotAFinding() {
        Report report = s1.analyse(of(good("CONTENT", 3)));

        assertThat(report.findings()).isEmpty();
        assertThat(report.clean()).extracting(Clean::what).contains("every_mark_describes_work");
    }

    @Test
    void chatterThatReadsLikeWorkIsNotCaughtAndTheGapIsDeclared() {
        Report report = s1.analyse(of(List.of(mark("n1", "ADS", "Fine by me, that gives us room."))));

        assertThat(report.findings()).isEmpty();
        assertThat(report.absences()).extracting(Absence::what).contains("chatter_that_reads_like_work");
        assertThat(report.saysSomething()).isTrue();
    }

    @Test
    void aQuestionIsCountedAsAQuestionRatherThanAsThinText() {
        Report report = s1.analyse(of(List.of(mark("n1", "CONTENT", "where are the Aurora captions?"))));

        assertThat(findingOf(report, "marks_without_a_description").because())
                .anyMatch(line -> line.contains("asked something rather than described work"));
    }

    @Test
    void everyUnusableMarkIsCountedExactlyOnceSoTheBreakdownAddsUp() {
        List<Snapshot.Node> marks = new ArrayList<>(good("CONTENT", 4));

        marks.add(mark("q1", "CONTENT", "why?"));
        marks.add(mark("nosignal1", "CONTENT", "..."));
        marks.add(mark("short1", "CONTENT", "done"));

        marks.add(mark("generic1", "CONTENT", "ok thanks update asap"));

        Report report = s1.analyse(of(marks));
        Finding headline = findingOf(report, "marks_without_a_description");

        assertThat(headline.reach()).isEqualTo(4);
        assertThat(headline.reachOf()).isEqualTo(8);
        assertThat(headline.evidence().get(Finding.EvidenceKind.NODE)).hasSize(4);

        int summed = java.util.Arrays.stream(
                        headline.because().getFirst().replace(".", "").split(", "))
                .mapToInt(part -> Integer.parseInt(part.trim().split(" ")[0]))
                .sum();
        assertThat(summed).isEqualTo(headline.reach());
    }

    @Test
    void theHeadlineCarriesTheIdsAndNotJustTheCount() {
        List<Snapshot.Node> marks = new ArrayList<>(good("CONTENT", 2));
        marks.add(mark("bad1", "CONTENT", "ok"));
        marks.add(mark("bad2", "CONTENT", "sure"));

        Finding headline = findingOf(s1.analyse(of(marks)), "marks_without_a_description");

        assertThat(headline.evidence().get(Finding.EvidenceKind.NODE)).containsExactlyInAnyOrder("bad1", "bad2");
        assertThat(headline.action()).isEqualTo("Ask for a sentence when the circle is clicked");
    }

    @Test
    void aKindOfWorkMarkedWorseThanTheRestIsCalledOutOnItsOwn() {
        List<Snapshot.Node> marks = new ArrayList<>(good("CONTENT", 8));
        for (int index = 0; index < 5; index++) {
            marks.add(mark("ads-bad-" + index, "ADS", "ok"));
        }

        Finding perType = findingOf(s1.analyse(of(marks)), "marks_without_a_description_by_work_type");

        assertThat(perType.subjectKind()).isEqualTo(SubjectKind.WORK_TYPE);
        assertThat(perType.subject()).isEqualTo("ADS");
        assertThat(perType.reach()).isEqualTo(5);
        assertThat(perType.reachOf()).isEqualTo(5);
    }

    @Test
    void aKindMarkedNoWorseThanEverythingElseAddsNoSecondFinding() {
        List<Snapshot.Node> marks = new ArrayList<>();
        for (String type : List.of("CONTENT", "ADS")) {
            marks.addAll(good(type, 4));
            marks.add(mark(type + "-bad", type, "ok"));
        }

        Report report = s1.analyse(of(marks));

        assertThat(report.findings()).extracting(Finding::kind).contains("marks_without_a_description");
        assertThat(report.findings())
                .extracting(Finding::kind)
                .doesNotContain("marks_without_a_description_by_work_type");
    }

    @Test
    void marksWithNoWorkTypeAreDeclaredSoTheFiguresReconcile() {
        List<Snapshot.Node> marks = new ArrayList<>(good("CONTENT", 3));
        marks.add(mark("untyped", null, "ok"));

        Report report = s1.analyse(of(marks));

        assertThat(report.absences()).extracting(Absence::what).contains("no_work_type");
        assertThat(findingOf(report, "marks_without_a_description").reachOf()).isEqualTo(4);
    }

    @Test
    void itAlwaysSaysSomething() {
        assertThat(s1.analyse(of(List.of())).saysSomething()).isTrue();
        assertThat(s1.analyse(of(good("CONTENT", 3))).saysSomething()).isTrue();
        assertThat(s1.analyse(of(List.of(mark("n1", "CONTENT", "ok")))).saysSomething())
                .isTrue();
    }
}
