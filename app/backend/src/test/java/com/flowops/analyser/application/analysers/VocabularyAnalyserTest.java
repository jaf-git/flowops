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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ANALYSER-RUN-01")
class VocabularyAnalyserTest {
    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-01T00:00:00Z");

    private final VocabularyAnalyser s2 = new VocabularyAnalyser();

    private static Snapshot.Node named(String id, String workType, String title) {
        return new Snapshot.Node(
                id, "job-1", workType, title, null, "text", "CLOSED", "TEXT", "WRITER", FROM, TO, List.of());
    }

    private static Snapshot.Template template(String id, String title, String workType) {
        return new Snapshot.Template(id, title, workType, "APPROVED", 1);
    }

    private static Snapshot of(List<Snapshot.Node> nodes, List<Snapshot.Template> templates) {
        return new Snapshot(FROM, TO, nodes, List.of(), List.of(), List.of(), templates, Snapshot.Shapes.none());
    }

    private static Snapshot afterMerging(String absorbed, String surviving, List<Snapshot.Template> templates) {
        return new Snapshot(
                FROM,
                TO,
                List.of(named("n-1", "CONTENT", "Write the caption")),
                List.of(),
                List.of(),
                List.of(),
                templates,
                Snapshot.Shapes.none(),
                List.of(),
                List.of(new Snapshot.MergedActivity(absorbed, surviving)));
    }

    @Test
    @DisplayName("a merge that stranded approved templates is re-raised on every run, under one identity")
    void strandedTemplatesAreReRaisedUntilSomebodyDecides() {
        Report report = new VocabularyAnalyser()
                .analyse(afterMerging(
                        "Post copy",
                        "Write the caption",
                        List.of(template("t-1", "Post copy", "CONTENT"), template("t-2", "Post copy", "CONTENT"))));

        Finding raised = findingOf(report, "templates_left_by_a_merge");

        assertThat(raised.headline()).contains("2 approved templates").contains("Post copy");
        assertThat(raised.action()).isEqualTo("Review them");
        assertThat(raised.evidence().get(Finding.EvidenceKind.TEMPLATE)).containsExactly("t-1", "t-2");
        assertThat(raised.key())
                .as("the merge raises the same key at merge time, so the second sighting is one finding "
                        + "seen twice rather than two findings - a dismissal made once stays made")
                .isEqualTo("S2_VOCABULARY:templates_left_by_a_merge:merged:post copy");
    }

    @Test
    @DisplayName("a merge that stranded nothing is silent, and so is one whose templates were rewritten")
    void nothingStrandedIsNotReported() {
        assertThat(new VocabularyAnalyser()
                        .analyse(afterMerging("Post copy", "Write the caption", List.of()))
                        .findings())
                .noneMatch(finding -> finding.kind().equals("templates_left_by_a_merge"));

        assertThat(new VocabularyAnalyser()
                        .analyse(afterMerging(
                                "Post copy",
                                "Write the caption",
                                List.of(template("t-1", "Write the caption", "CONTENT"))))
                        .findings())
                .as("a template renamed to the surviving activity is no longer stranded")
                .noneMatch(finding -> finding.kind().equals("templates_left_by_a_merge"));
    }

    private static Finding findingOf(Report report, String kind) {
        return report.findings().stream()
                .filter(finding -> finding.kind().equals(kind))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + kind + " in " + report.findings()));
    }

    @Test
    void withNothingNamedTheAnswerIsAStatedPrecondition() {
        Report report = s2.analyse(of(List.of(named("n1", null, "caption set")), List.of()));

        assertThat(report.findings()).isEmpty();
        assertThat(report.wasBlocked()).isTrue();
        assertThat(report.saysSomething()).isTrue();
    }

    @Test
    void oneKindOfWorkCalledManyThingsIsDrift() {
        List<Snapshot.Node> nodes = List.of(
                named("n1", "CONTENT", "caption set"),
                named("n2", "CONTENT", "captions"),
                named("n3", "CONTENT", "post copy"),
                named("n4", "CONTENT", "social words"));

        Finding drift = findingOf(s2.analyse(of(nodes, List.of())), "one_kind_of_work_many_names");

        assertThat(drift.because().getFirst()).contains("CONTENT").contains("4 different things");
        assertThat(drift.action()).isEqualTo("Agree on one word for each");
        assertThat(drift.reach()).isEqualTo(1);
        assertThat(drift.reachOf()).isEqualTo(1);
    }

    @Test
    void threeNamesForOneKindOfWorkIsNotYetDrift() {
        List<Snapshot.Node> nodes = List.of(
                named("n1", "CONTENT", "caption set"),
                named("n2", "CONTENT", "captions"),
                named("n3", "CONTENT", "post copy"));

        Report report = s2.analyse(of(nodes, List.of()));

        assertThat(report.findings()).extracting(Finding::kind).doesNotContain("one_kind_of_work_many_names");
        assertThat(report.clean()).extracting(Clean::what).contains("one_name_per_kind_of_work");
    }

    @Test
    void oneNameUsedForManyKindsOfWorkIsCollapse() {
        List<Snapshot.Node> nodes = List.of(
                named("n1", "CONTENT", "update"), named("n2", "ADS", "update"), named("n3", "REPORTING", "update"));

        Finding collapse = findingOf(s2.analyse(of(nodes, List.of())), "one_name_many_kinds_of_work");

        assertThat(collapse.because().getFirst()).contains("update").contains("3 different kinds");
        assertThat(collapse.action()).isEqualTo("Split the name");
    }

    @Test
    void driftAndCollapseAreTwoFindingsWithTwoVerbs() {
        List<Snapshot.Node> nodes = new ArrayList<>(List.of(
                named("n1", "CONTENT", "caption set"),
                named("n2", "CONTENT", "captions"),
                named("n3", "CONTENT", "post copy"),
                named("n4", "CONTENT", "social words")));
        nodes.addAll(List.of(
                named("n5", "CONTENT", "update"), named("n6", "ADS", "update"), named("n7", "PHOTO", "update")));

        Report report = s2.analyse(of(nodes, List.of()));

        assertThat(report.findings())
                .extracting(Finding::kind)
                .contains("one_kind_of_work_many_names", "one_name_many_kinds_of_work");
        assertThat(findingOf(report, "one_kind_of_work_many_names").action())
                .isNotEqualTo(findingOf(report, "one_name_many_kinds_of_work").action());
    }

    @Test
    void spellingsOfOneNameAreNotCountedAsTwo() {
        List<Snapshot.Node> nodes = List.of(
                named("n1", "CONTENT", "Caption Set"),
                named("n2", "CONTENT", "caption  set"),
                named("n3", "CONTENT", "captions"),
                named("n4", "CONTENT", "post copy"));

        Report report = s2.analyse(of(nodes, List.of()));

        assertThat(report.clean()).extracting(Clean::what).contains("one_name_per_kind_of_work");
    }

    @Test
    void templatesCountTowardsTheVocabularyAlongsideWhatPeopleType() {
        List<Snapshot.Template> templates = List.of(
                template("t1", "caption set", "CONTENT"),
                template("t2", "captions", "CONTENT"),
                template("t3", "post copy", "CONTENT"),
                template("t4", "social words", "CONTENT"));

        Finding drift = findingOf(s2.analyse(of(List.of(), templates)), "one_kind_of_work_many_names");

        assertThat(drift.because().getFirst()).contains("4 different things");
    }

    @Test
    void itAlwaysSaysThatNamesDifferingOnlyInMeaningAreInvisibleToIt() {
        Report report = s2.analyse(of(List.of(named("n1", "CONTENT", "caption set")), List.of()));

        assertThat(report.absences()).extracting(Absence::what).contains("names_that_differ_only_in_meaning");
    }

    @Test
    void itAlwaysSaysSomething() {
        assertThat(s2.analyse(of(List.of(), List.of())).saysSomething()).isTrue();
        assertThat(s2.analyse(of(List.of(named("n1", "CONTENT", "caption set")), List.of()))
                        .saysSomething())
                .isTrue();
    }
}
