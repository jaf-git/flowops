package com.flowops.analyser.application.analysers;

import com.flowops.analyser.domain.Absence;
import com.flowops.analyser.domain.Analyser;
import com.flowops.analyser.domain.AnalyserStage;
import com.flowops.analyser.domain.BracketOutcome;
import com.flowops.analyser.domain.Category;
import com.flowops.analyser.domain.Clean;
import com.flowops.analyser.domain.Confidence;
import com.flowops.analyser.domain.Finding;
import com.flowops.analyser.domain.Precondition;
import com.flowops.analyser.domain.Report;
import com.flowops.analyser.domain.Severity;
import com.flowops.analyser.domain.Snapshot;
import com.flowops.analyser.domain.SubjectKind;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class EffortAnalyser implements Analyser {
    private static final int ENOUGH_TO_TAKE_A_MEDIAN = 3;

    private static final long MINUTES_IN_TWO_HOURS = 120;

    @Override
    public AnalyserStage stage() {
        return AnalyserStage.MEASURE;
    }

    @Override
    public String id() {
        return "S3_EFFORT";
    }

    @Override
    public Category category() {
        return Category.YOUR_TIME;
    }

    @Override
    public Report analyse(Snapshot snapshot) {
        List<Snapshot.Bracket> brackets = snapshot.brackets();
        List<Snapshot.Bracket> finished = brackets.stream()
                .filter(bracket -> bracket.outcome().contributesADuration())
                .toList();

        if (finished.size() < ENOUGH_TO_TAKE_A_MEDIAN) {
            return Report.blocked(
                    id(),
                    brackets.size(),
                    Precondition.unmet(
                            "at least %d finished pieces of work".formatted(ENOUGH_TO_TAKE_A_MEDIAN),
                            "%d of %d in this window are finished".formatted(finished.size(), brackets.size()),
                            "Finish more of the work already marked"));
        }

        List<Finding> findings = new ArrayList<>();
        List<Absence> absences = new ArrayList<>();
        List<Clean> clean = new ArrayList<>();

        howLongWorkTakes(finished, brackets).ifPresent(findings::add);

        List<Snapshot.Node> trailed =
                snapshot.nodes().stream().filter(Snapshot.Node::hasATrail).toList();

        noteTheNodesWithNoHistory(snapshot, trailed, absences);

        if (trailed.isEmpty()) {
            absences.add(new Absence(
                    "no_trail",
                    ("Nothing in this window has a recorded history, so stalling, rework and handovers "
                            + "cannot be measured. Only work marked after the trail existed carries one."),
                    true));
        } else {
            fromTheTrail(trailed, findings, clean);
        }

        return new Report(
                id(),
                brackets.size(),
                findings,
                absences,
                clean,
                List.of(Precondition.met(
                        "at least %d finished pieces of work".formatted(ENOUGH_TO_TAKE_A_MEDIAN),
                        "%d of %d in this window".formatted(finished.size(), brackets.size()))));
    }

    private Optional<Finding> howLongWorkTakes(List<Snapshot.Bracket> finished, List<Snapshot.Bracket> all) {
        List<Long> minutes = finished.stream()
                .filter(bracket -> bracket.openedAt() != null && bracket.closedAt() != null)
                .map(bracket ->
                        Duration.between(bracket.openedAt(), bracket.closedAt()).toMinutes())
                .sorted()
                .toList();

        if (minutes.isEmpty()) {
            return Optional.empty();
        }

        long median = minutes.get(minutes.size() / 2);

        if (median == 0) {
            return Optional.empty();
        }

        Map<BracketOutcome, Long> byOutcome = countByOutcome(all);

        List<String> because = new ArrayList<>();
        because.add("Measured over %d finished pieces of work — delivered or done, and nothing else."
                .formatted(minutes.size()));
        because.add("%d still open, %d abandoned, %d closed because something above them closed, %d handed over."
                .formatted(
                        byOutcome.getOrDefault(BracketOutcome.OPEN, 0L),
                        byOutcome.getOrDefault(BracketOutcome.ABANDONED, 0L),
                        byOutcome.getOrDefault(BracketOutcome.CONTAINER_CLOSED, 0L),
                        byOutcome.getOrDefault(BracketOutcome.DISRUPTED, 0L)));
        because.add("None of those four is in the median. Work closed because its engagement ended "
                + "carries a closing time that looks exactly like a completion, and counting it would "
                + "shorten this number with nothing to show that it had.");

        return Optional.of(new Finding(
                id(),
                "median_time_to_finish",
                SubjectKind.WORKSPACE,
                "workspace",
                category(),
                "Work that finishes takes about %s".formatted(inWords(median)),
                because,
                Map.of(
                        Finding.EvidenceKind.BRACKET,
                        finished.stream().map(Snapshot.Bracket::id).toList()),
                Severity.LOW,
                minutes.size() >= 10 ? Confidence.HIGH : Confidence.MEDIUM,
                minutes.size(),
                all.size(),
                "Compare against what you quote"));
    }

    private static String inWords(long minutes) {
        if (minutes < MINUTES_IN_TWO_HOURS) {
            return minutes == 1 ? "1 minute" : "%d minutes".formatted(minutes);
        }
        long hours = minutes / 60;
        return hours == 1 ? "1 hour" : "%d hours".formatted(hours);
    }

    private void fromTheTrail(List<Snapshot.Node> trailed, List<Finding> findings, List<Clean> clean) {
        countedTrail(
                        trailed,
                        node -> node.timesItReached("BLOCKED") > 0,
                        "work_that_stalled",
                        "%d of %d pieces of work stopped to wait for somebody",
                        "Waiting is not slowness — whose clock it was is S4's question, and this one only "
                                + "counts how often work stopped.",
                        "Look at what they were waiting for")
                .ifPresentOrElse(
                        findings::add,
                        () -> clean.add(new Clean(
                                "nothing_stalled",
                                "%d pieces of work checked; none of them stopped to wait.".formatted(trailed.size()))));

        countedTrail(
                        trailed,
                        node -> node.timesItReached("IN_PROGRESS") > 1,
                        "work_that_came_back",
                        "%d of %d pieces of work were reopened after being finished",
                        "Work that comes back was finished once and was not right. The cost is paid twice "
                                + "and only the second time is visible in a completion date.",
                        "Look at what came back")
                .ifPresent(findings::add);

        countedTrail(
                        trailed,
                        node -> node.timesItReached("BOUNCED") > 0,
                        "work_that_changed_hands",
                        "%d of %d pieces of work were handed back",
                        "Work handed back was assigned to somebody who could not take it. That is a "
                                + "staffing fact rather than a performance one.",
                        "Look at how work is assigned")
                .ifPresent(findings::add);
    }

    private Optional<Finding> countedTrail(
            List<Snapshot.Node> trailed,
            java.util.function.Predicate<Snapshot.Node> matches,
            String kind,
            String headline,
            String because,
            String action) {
        List<Snapshot.Node> hit = trailed.stream().filter(matches).toList();
        if (hit.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new Finding(
                id(),
                kind,
                SubjectKind.WORKSPACE,
                "workspace",
                category(),
                headline.formatted(hit.size(), trailed.size()),
                List.of(
                        because,
                        "Counted only over work whose history is recorded — %d of the pieces of work in "
                                        .formatted(trailed.size())
                                + "this window carry one."),
                Map.of(
                        Finding.EvidenceKind.NODE,
                        hit.stream().map(Snapshot.Node::id).toList()),
                Severity.MEDIUM,
                Confidence.HIGH,
                hit.size(),
                trailed.size(),
                action));
    }

    private void noteTheNodesWithNoHistory(Snapshot snapshot, List<Snapshot.Node> trailed, List<Absence> absences) {
        int without = snapshot.nodes().size() - trailed.size();
        if (without > 0) {
            absences.add(new Absence(
                    "no_trail",
                    ("%d of %d pieces of work were marked before their history was recorded. Their "
                                    + "stalling, rework and handovers are gone rather than absent, so every figure "
                                    + "above is out of the %d that carry one.")
                            .formatted(without, snapshot.nodes().size(), trailed.size()),
                    false));
        }
    }

    private static Map<BracketOutcome, Long> countByOutcome(List<Snapshot.Bracket> brackets) {
        return brackets.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        Snapshot.Bracket::outcome, java.util.stream.Collectors.counting()));
    }
}
