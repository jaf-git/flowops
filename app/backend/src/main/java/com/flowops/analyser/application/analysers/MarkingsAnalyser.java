package com.flowops.analyser.application.analysers;

import com.flowops.analyser.domain.Absence;
import com.flowops.analyser.domain.Analyser;
import com.flowops.analyser.domain.AnalyserStage;
import com.flowops.analyser.domain.Category;
import com.flowops.analyser.domain.Clean;
import com.flowops.analyser.domain.Confidence;
import com.flowops.analyser.domain.Finding;
import com.flowops.analyser.domain.Precondition;
import com.flowops.analyser.domain.Report;
import com.flowops.analyser.domain.Severity;
import com.flowops.analyser.domain.Snapshot;
import com.flowops.analyser.domain.SubjectKind;
import com.flowops.shared.text.Intent;
import com.flowops.shared.text.Words;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MarkingsAnalyser implements Analyser {
    private static final double PLAUSIBLE_FLOOR = 0.5;

    private static final double UNIQUE_FLOOR = 0.5;

    private static final int TEXT_FLOOR = 12;

    private static final double QUALITY_FLOOR = 0.25;

    private static final int ENOUGH_TO_JUDGE_A_HABIT = 5;

    private enum Unusable {
        A_QUESTION("questions", "asked something rather than described work"),
        NO_SIGNAL("no signal", "carry no readable words at all"),
        BELOW_TEXT_FLOOR("text floor", "are too short to say what the work was"),
        BELOW_QUALITY_FLOOR("below floor", "are words without a subject — chatter rather than a description");

        private final String label;
        private final String phrase;

        Unusable(String label, String phrase) {
            this.label = label;
            this.phrase = phrase;
        }
    }

    @Override
    public AnalyserStage stage() {
        return AnalyserStage.DETECT;
    }

    @Override
    public String id() {
        return "S1_MARKINGS";
    }

    @Override
    public Category category() {
        return Category.YOUR_WORK;
    }

    @Override
    public Report analyse(Snapshot snapshot) {
        List<Snapshot.Node> marks = snapshot.nodes();

        if (marks.isEmpty()) {
            return Report.blocked(
                    id(),
                    0,
                    Precondition.unmet(
                            "somebody to have marked work",
                            "nothing was marked in this window",
                            "Mark a message as work"));
        }

        Map<Snapshot.Node, Unusable> unusable = new LinkedHashMap<>();
        for (Snapshot.Node mark : marks) {
            judge(mark).ifPresent(reason -> unusable.put(mark, reason));
        }

        List<Finding> findings = new ArrayList<>();
        List<Absence> absences = new ArrayList<>();
        List<Clean> clean = new ArrayList<>();

        if (unusable.isEmpty()) {
            clean.add(new Clean(
                    "every_mark_describes_work",
                    "%d marks checked; every one says what the work was.".formatted(marks.size())));
        } else {
            findings.add(theHeadline(marks, unusable));
            findings.addAll(byWorkType(marks, unusable));
        }

        noteTheMarksWithNoWorkType(marks, absences);
        noteTheChatterItCannotSee(absences);

        return new Report(
                id(),
                marks.size(),
                findings,
                absences,
                clean,
                List.of(Precondition.met(
                        "somebody to have marked work", "%d marks in this window".formatted(marks.size()))));
    }

    private Finding theHeadline(List<Snapshot.Node> marks, Map<Snapshot.Node, Unusable> unusable) {
        Map<Unusable, Long> byReason = unusable.values().stream()
                .collect(Collectors.groupingBy(
                        reason -> reason, () -> new EnumMap<>(Unusable.class), Collectors.counting()));

        List<String> because = new ArrayList<>();
        because.add(byReason.entrySet().stream()
                        .map(entry -> "%d %s".formatted(entry.getValue(), entry.getKey().label))
                        .collect(Collectors.joining(", "))
                + ".");
        for (Map.Entry<Unusable, Long> reason : byReason.entrySet()) {
            because.add("%d %s.".formatted(reason.getValue(), reason.getKey().phrase));
        }
        because.add("This is the reason the other analysers go quiet: a mark that does not say what the "
                + "work was cannot be clustered, named or compared with anything.");

        return new Finding(
                id(),
                "marks_without_a_description",
                SubjectKind.WORKSPACE,
                "workspace",
                category(),
                "%d of %d marks do not say what the work was".formatted(unusable.size(), marks.size()),
                because,
                Map.of(
                        Finding.EvidenceKind.NODE,
                        unusable.keySet().stream().map(Snapshot.Node::id).toList()),
                Severity.HIGH,
                Confidence.HIGH,
                unusable.size(),
                marks.size(),
                "Ask for a sentence when the circle is clicked");
    }

    private List<Finding> byWorkType(List<Snapshot.Node> marks, Map<Snapshot.Node, Unusable> unusable) {
        double overall = (double) unusable.size() / marks.size();

        Map<String, List<Snapshot.Node>> byType = marks.stream()
                .filter(mark -> mark.workType() != null && !mark.workType().isBlank())
                .collect(Collectors.groupingBy(Snapshot.Node::workType, LinkedHashMap::new, Collectors.toList()));

        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<String, List<Snapshot.Node>> kind : byType.entrySet()) {
            List<Snapshot.Node> bad =
                    kind.getValue().stream().filter(unusable::containsKey).toList();

            if (kind.getValue().size() < ENOUGH_TO_JUDGE_A_HABIT || bad.isEmpty()) {
                continue;
            }
            double share = (double) bad.size() / kind.getValue().size();
            if (share <= overall) {
                continue;
            }

            findings.add(new Finding(
                    id(),
                    "marks_without_a_description_by_work_type",
                    SubjectKind.WORK_TYPE,
                    kind.getKey(),
                    category(),
                    "%s is marked worse than the rest — %d of %d say nothing"
                            .formatted(
                                    kind.getKey(), bad.size(), kind.getValue().size()),
                    List.of(
                            "%.0f%% of %s marks carry no description, against %.0f%% across everything."
                                    .formatted(share * 100, kind.getKey(), overall * 100),
                            "One kind of work being marked worse than the rest is usually a habit in one "
                                    + "conversation rather than a problem with the people doing it."),
                    Map.of(
                            Finding.EvidenceKind.NODE,
                            bad.stream().map(Snapshot.Node::id).toList()),
                    Severity.MEDIUM,
                    Confidence.MEDIUM,
                    bad.size(),
                    kind.getValue().size(),
                    "Look at how this work gets marked"));
        }
        return findings;
    }

    private java.util.Optional<Unusable> judge(Snapshot.Node mark) {
        String text = mark.text() == null ? "" : mark.text();

        if (Intent.of(text, mark.detail()) == Intent.QUESTION) {
            return java.util.Optional.of(Unusable.A_QUESTION);
        }
        if (Words.tokens(text).isEmpty()) {
            return java.util.Optional.of(Unusable.NO_SIGNAL);
        }
        if (Words.normalise(Words.withoutLinksOrMarkup(text)).length() < TEXT_FLOOR) {
            return java.util.Optional.of(Unusable.BELOW_TEXT_FLOOR);
        }
        if (Words.quality(text, PLAUSIBLE_FLOOR, UNIQUE_FLOOR) < QUALITY_FLOOR) {
            return java.util.Optional.of(Unusable.BELOW_QUALITY_FLOOR);
        }
        return java.util.Optional.empty();
    }

    private void noteTheChatterItCannotSee(List<Absence> absences) {
        absences.add(new Absence(
                "chatter_that_reads_like_work",
                ("A mark that is long, well-formed and still says nothing about work is not counted here. "
                        + "\"Fine by me, that gives us room.\" passes every mechanical floor; only meaning "
                        + "separates it from a real description, so the real figure is higher than this one."),
                false));
    }

    private void noteTheMarksWithNoWorkType(List<Snapshot.Node> marks, List<Absence> absences) {
        long untyped = marks.stream()
                .filter(mark -> mark.workType() == null || mark.workType().isBlank())
                .count();
        if (untyped > 0) {
            absences.add(new Absence(
                    "no_work_type",
                    ("%d of %d marks carry no governed kind of work, so they are in the overall figure and "
                                    + "in none of the per-kind ones.")
                            .formatted(untyped, marks.size()),
                    false));
        }
    }
}
