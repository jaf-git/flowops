package com.flowops.analyser.application.analysers;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.analyser.domain.Absence;
import com.flowops.analyser.domain.Clean;
import com.flowops.analyser.domain.Confidence;
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
class TemplateSuggestionTest {
    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-01T00:00:00Z");

    private final TemplateSuggestion s9 = new TemplateSuggestion();

    private static Snapshot.Node node(String id, String workType, String title, String outputType) {
        return new Snapshot.Node(
                id,
                "job-1",
                workType,
                title,
                null,
                "some marked sentence",
                "CLOSED",
                outputType,
                "WRITER",
                FROM,
                TO,
                List.of());
    }

    private static Snapshot of(List<Snapshot.Node> nodes, List<Snapshot.Template> templates) {
        return new Snapshot(FROM, TO, nodes, List.of(), List.of(), List.of(), templates, Snapshot.Shapes.none());
    }

    private static List<Snapshot.Node> titled(String workType, String title, int count) {
        List<Snapshot.Node> nodes = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            nodes.add(node(workType + "-" + index, workType, title, "TEXT"));
        }
        return nodes;
    }

    private static Report reportOf(Snapshot snapshot, TemplateSuggestion s9) {
        return s9.analyse(snapshot);
    }

    @Test
    void withoutAGovernedWorkTypeTheAnswerIsAStatedPreconditionRatherThanSilence() {
        Snapshot snapshot = of(List.of(node("n1", null, "caption set", "TEXT")), List.of());

        Report report = reportOf(snapshot, s9);

        assertThat(report.findings()).isEmpty();
        assertThat(report.preconditions()).hasSize(1);
        assertThat(report.preconditions().getFirst().met()).isFalse();
        assertThat(report.wasBlocked()).isTrue();
    }

    @Test
    void repeatedWorkThatPeopleHaveNamedBecomesADraft() {
        Snapshot snapshot = of(titled("CONTENT", "caption set for Aurora", 3), List.of());

        Report report = reportOf(snapshot, s9);

        assertThat(report.findings()).hasSize(1);
        Finding draft = report.findings().getFirst();
        assertThat(draft.headline()).contains("caption set for Aurora");
        assertThat(draft.subjectKind()).isEqualTo(SubjectKind.WORK_TYPE);
        assertThat(draft.subject()).isEqualTo("CONTENT");
        assertThat(draft.action()).isEqualTo("Write it down as a template");
        assertThat(draft.confidence()).isEqualTo(Confidence.HIGH);
    }

    @Test
    void aDraftStatesWhatItReachesAndWhatThatIsOutOf() {
        List<Snapshot.Node> nodes = new ArrayList<>(titled("CONTENT", "caption set", 3));
        nodes.addAll(titled("ADS", "ad variant", 2));

        Finding draft = reportOf(of(nodes, List.of()), s9).findings().getFirst();

        assertThat(draft.reach()).isEqualTo(3);
        assertThat(draft.reachOf()).isEqualTo(5);
        assertThat(draft.share()).hasValue(0.6);
    }

    @Test
    void spellingsOfOneTitleAreOneClusterAndTheTypedFormIsWhatIsProposed() {
        List<Snapshot.Node> nodes = List.of(
                node("a", "CONTENT", "Caption Set", "TEXT"),
                node("b", "CONTENT", "caption  set", "TEXT"),
                node("c", "CONTENT", "Caption Set", "TEXT"));

        Report report = reportOf(of(nodes, List.of()), s9);

        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().getFirst().headline()).contains("Caption Set");
    }

    @Test
    void differentOutputKindsDoNotSplitOneKindOfWork() {
        List<Snapshot.Node> nodes = List.of(
                node("a", "CONTENT", "caption set", "TEXT"),
                node("b", "CONTENT", "caption set", "DESIGN"),
                node("c", "CONTENT", "caption set", null));

        Report report = reportOf(of(nodes, List.of()), s9);

        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().getFirst().reach()).isEqualTo(3);
    }

    @Test
    void workAlreadyCoveredByALiveTemplateIsReportedAsCleanRatherThanSuggestedAgain() {
        Snapshot snapshot = of(
                titled("CONTENT", "caption set", 3),
                List.of(new Snapshot.Template("t1", "Caption set", "CONTENT", "APPROVED", 7)));

        Report report = reportOf(snapshot, s9);

        assertThat(report.findings()).isEmpty();
        assertThat(report.clean()).extracting(Clean::what).contains("already_covered");
        assertThat(report.saysSomething()).isTrue();
    }

    @Test
    void aTemplateThatHasNeverMatchedWorkCoversNothing() {
        Snapshot snapshot = of(
                titled("CONTENT", "caption set", 3),
                List.of(new Snapshot.Template("t1", "Caption set", "CONTENT", "APPROVED", 0)));

        Report report = reportOf(snapshot, s9);

        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().getFirst().subject()).isEqualTo("CONTENT");
    }

    @Test
    void workSeenTwiceIsNotYetAPatternAndTheReasonIsStated() {
        Report report = reportOf(of(titled("CONTENT", "caption set", 2), List.of()), s9);

        assertThat(report.findings()).isEmpty();
        assertThat(report.absences()).extracting(Absence::what).contains("below_the_repetition_floor");
    }

    @Test
    void aClusterNobodyHasNamedIsSuppressedAndTheReasonIsStated() {
        List<Snapshot.Node> nameless = List.of(
                node("a", "CONTENT", null, "TEXT"),
                node("b", "CONTENT", null, "TEXT"),
                node("c", "CONTENT", null, "TEXT"),
                node("d", "CONTENT", null, "TEXT"),
                node("e", "CONTENT", null, "TEXT"));

        Report report = reportOf(of(nameless, List.of()), s9);

        assertThat(report.findings()).isEmpty();
        assertThat(report.absences()).extracting(Absence::what).contains("no_title");
    }

    @Test
    void aThinlyNamedClusterIsProposedWithLessConfidenceRatherThanSuppressed() {
        List<Snapshot.Node> nodes = List.of(
                node("a", "CONTENT", "caption set", "TEXT"),
                node("b", "CONTENT", null, "TEXT"),
                node("c", "CONTENT", null, "TEXT"),
                node("d", "CONTENT", null, "TEXT"),
                node("e", "CONTENT", null, "TEXT"));

        Report report = reportOf(of(nodes, List.of()), s9);

        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().getFirst().confidence()).isEqualTo(Confidence.MEDIUM);
    }

    @Test
    void theOutputKindsNoNodeCanReachAreAlwaysDeclared() {
        Report report = reportOf(of(titled("CONTENT", "caption set", 3), List.of()), s9);

        assertThat(report.absences()).extracting(Absence::what).contains("output_kinds_unreachable_from_a_node");
    }

    @Test
    void itAlwaysSaysSomething() {
        assertThat(reportOf(of(titled("CONTENT", "caption set", 3), List.of()), s9)
                        .saysSomething())
                .isTrue();
        assertThat(reportOf(of(titled("CONTENT", "caption set", 1), List.of()), s9)
                        .saysSomething())
                .isTrue();
        assertThat(reportOf(of(List.of(node("a", "CONTENT", null, null)), List.of()), s9)
                        .saysSomething())
                .isTrue();
    }

    @Test
    void theDraftStatesThatItsOptionalFieldsAreEmptyOnPurpose() {
        Finding draft = reportOf(of(titled("CONTENT", "caption set", 3), List.of()), s9)
                .findings()
                .getFirst();

        assertThat(draft.because()).anyMatch(line -> line.contains("left empty deliberately"));
        assertThat(draft.evidence().get(Finding.EvidenceKind.NODE)).hasSize(3);
    }

    @Test
    void noSentenceEverShowsAnIdentifierToTheReader() {
        List<Snapshot.Node> nodes = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            nodes.add(new Snapshot.Node(
                    "content-" + index,
                    "job-1",
                    "CONTENT",
                    "caption set for Aurora",
                    null,
                    "some marked sentence",
                    "CLOSED",
                    "TEXT",
                    "0c59a4d5-41d1-43b4-abae-ac02352fb888",
                    FROM,
                    TO,
                    List.of()));
        }

        Finding draft = reportOf(of(nodes, List.of()), s9).findings().getFirst();

        for (String sentence : draft.because()) {
            assertThat(sentence)
                    .as("a person reads this; a role id tells them nothing and looks like a defect: %s", sentence)
                    .doesNotMatch(".*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*");
        }

        assertThat(draft.because())
                .as("the claim that one role does it survives; only the unusable identifier goes")
                .anyMatch(sentence -> sentence.contains("same role"));
    }
}
