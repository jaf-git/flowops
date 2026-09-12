package com.flowops.analyser.application.analysers;

import com.flowops.analyser.domain.Absence;
import com.flowops.analyser.domain.Analyser;
import com.flowops.analyser.domain.AnalyserStage;
import com.flowops.analyser.domain.Category;
import com.flowops.analyser.domain.Clean;
import com.flowops.analyser.domain.Confidence;
import com.flowops.analyser.domain.Finding;
import com.flowops.analyser.domain.OutputVocabulary;
import com.flowops.analyser.domain.Precondition;
import com.flowops.analyser.domain.Report;
import com.flowops.analyser.domain.Severity;
import com.flowops.analyser.domain.Snapshot;
import com.flowops.analyser.domain.SubjectKind;
import com.flowops.analyser.domain.WorkName;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TemplateSuggestion implements Analyser {
    private static final int REPETITION_FLOOR = 3;

    private static final double HIGH_ENRICHMENT = 0.5;

    private static final double MINIMUM_ENRICHMENT = 0.2;

    @Override
    public AnalyserStage stage() {
        return AnalyserStage.RECOMMEND;
    }

    @Override
    public String id() {
        return "S9_TEMPLATE";
    }

    @Override
    public Category category() {
        return Category.YOUR_WORK;
    }

    @Override
    public Report analyse(Snapshot snapshot) {
        List<Snapshot.Node> eligible = snapshot.nodes().stream()
                .filter(TemplateSuggestion::countsAsWork)
                .toList();

        if (eligible.isEmpty()) {
            return Report.blocked(
                    id(),
                    snapshot.nodes().size(),
                    Precondition.unmet(
                            "units of work carrying a governed work type",
                            "Give people a job on the Organisation screen, so their work takes a kind",
                            "none of the %d in this window carry one"
                                    .formatted(snapshot.nodes().size())));
        }

        Set<String> coveredByALiveTemplate = snapshot.templates().stream()
                .filter(Snapshot.Template::hasMatchedWork)
                .map(Snapshot.Template::workType)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, List<Snapshot.Node>> byWorkType = eligible.stream()
                .collect(Collectors.groupingBy(Snapshot.Node::workType, LinkedHashMap::new, Collectors.toList()));

        List<Finding> findings = new ArrayList<>();
        List<Absence> absences = new ArrayList<>();
        List<Clean> clean = new ArrayList<>();

        int tooFewToRepeat = 0;
        int alreadyCovered = 0;
        int tooLittleSaid = 0;

        for (Map.Entry<String, List<Snapshot.Node>> cluster : byWorkType.entrySet()) {
            String workType = cluster.getKey();
            List<Snapshot.Node> nodes = cluster.getValue();

            if (nodes.size() < REPETITION_FLOOR) {
                tooFewToRepeat++;
                continue;
            }
            if (coveredByALiveTemplate.contains(workType)) {
                alreadyCovered++;
                continue;
            }

            double enriched = enrichedShare(nodes);
            if (enriched < MINIMUM_ENRICHMENT) {
                tooLittleSaid++;
                continue;
            }

            Optional<String> title = WorkName.commonestTitle(nodes);
            if (title.isEmpty()) {
                tooLittleSaid++;
                continue;
            }

            findings.add(draft(workType, nodes, title.get(), enriched, eligible.size(), snapshot));
        }

        describeWhatWasSkipped(absences, clean, byWorkType.size(), tooFewToRepeat, alreadyCovered, tooLittleSaid);
        noteTheOutputKindsNoNodeCanReach(absences);

        if (findings.isEmpty() && absences.isEmpty() && clean.isEmpty()) {
            clean.add(new Clean(
                    "nothing_to_suggest",
                    "Read %d units of work; none repeats often enough to deserve a template yet."
                            .formatted(eligible.size())));
        }

        return new Report(
                id(),
                eligible.size(),
                findings,
                absences,
                clean,
                List.of(Precondition.met(
                        "units of work carrying a governed work type",
                        "%d of %d in this window"
                                .formatted(eligible.size(), snapshot.nodes().size()))));
    }

    private Finding draft(
            String workType,
            List<Snapshot.Node> nodes,
            String title,
            double enriched,
            int allWorkNodes,
            Snapshot snapshot) {
        List<String> because = new ArrayList<>();
        because.add("\"%s\" is what people call this work, across %d units of it.".formatted(title, nodes.size()));
        because.add("Governed work type: %s.".formatted(workType));

        if (commonestRole(nodes).isPresent()) {
            because.add("The same role does it every time.");
        }

        commonestOutputKind(nodes).ifPresent(kind -> because.add("It usually produces: %s.".formatted(kind)));

        medianHoursFor(workType, snapshot)
                .filter(hours -> hours > 0)
                .ifPresent(hours -> because.add("Finished work of this kind takes about %s hours.".formatted(hours)));

        because.add("The description and checklist are left empty deliberately — a draft that looks "
                + "complete gets approved unread.");

        return new Finding(
                id(),
                "repeated_work_without_a_template",
                SubjectKind.WORK_TYPE,
                workType,
                category(),
                "\"%s\" happens often enough to be worth writing down".formatted(title),
                because,
                Map.of(
                        Finding.EvidenceKind.NODE,
                        nodes.stream().map(Snapshot.Node::id).toList()),
                Severity.MEDIUM,
                enriched >= HIGH_ENRICHMENT ? Confidence.HIGH : Confidence.MEDIUM,
                nodes.size(),
                allWorkNodes,
                "Write it down as a template");
    }

    private void describeWhatWasSkipped(
            List<Absence> absences,
            List<Clean> clean,
            int clusters,
            int tooFewToRepeat,
            int alreadyCovered,
            int tooLittleSaid) {
        if (tooFewToRepeat > 0) {
            absences.add(new Absence(
                    "below_the_repetition_floor",
                    "%d of %d kinds of work appear fewer than %d times, which is not yet a pattern."
                            .formatted(tooFewToRepeat, clusters, REPETITION_FLOOR),
                    false));
        }
        if (tooLittleSaid > 0) {
            absences.add(new Absence(
                    "no_title",
                    ("%d kinds of work repeat often enough but nobody has named them. A suggestion needs the "
                                    + "title people actually type; inventing one is the thing it must not do.")
                            .formatted(tooLittleSaid),
                    false));
        }
        if (alreadyCovered > 0) {
            clean.add(new Clean(
                    "already_covered",
                    "%d kinds of work are already covered by a template that has matched real work."
                            .formatted(alreadyCovered)));
        }
    }

    private void noteTheOutputKindsNoNodeCanReach(List<Absence> absences) {
        absences.add(new Absence(
                "output_kinds_unreachable_from_a_node",
                ("A template can say its output is %s; a unit of work cannot. Suggestions for that kind of "
                                + "work will never propose it.")
                        .formatted(String.join(" or ", OutputVocabulary.UNREACHABLE_FROM_A_NODE)),
                false));
    }

    private static boolean countsAsWork(Snapshot.Node node) {
        return node.workType() != null && !node.workType().isBlank();
    }

    private static double enrichedShare(List<Snapshot.Node> nodes) {
        long titled = nodes.stream().filter(Snapshot.Node::isEnriched).count();
        return (double) titled / nodes.size();
    }

    private static Optional<String> commonestRole(List<Snapshot.Node> nodes) {
        return commonest(nodes.stream()
                .map(Snapshot.Node::performerRoleId)
                .filter(java.util.Objects::nonNull)
                .toList());
    }

    private static Optional<String> commonestOutputKind(List<Snapshot.Node> nodes) {
        return commonest(nodes.stream()
                .map(Snapshot.Node::outputType)
                .map(OutputVocabulary::templateOutputKindFor)
                .flatMap(Optional::stream)
                .toList());
    }

    private static Optional<String> commonest(List<String> values) {
        return values.stream().collect(Collectors.groupingBy(value -> value, Collectors.counting())).entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    private static Optional<Long> medianHoursFor(String workType, Snapshot snapshot) {
        List<Long> hours = snapshot.brackets().stream()
                .filter(bracket -> workType.equals(bracket.workType()))
                .filter(bracket -> bracket.outcome().contributesADuration())
                .filter(bracket -> bracket.openedAt() != null && bracket.closedAt() != null)
                .map(bracket ->
                        Duration.between(bracket.openedAt(), bracket.closedAt()).toHours())
                .sorted()
                .toList();

        return hours.isEmpty() ? Optional.empty() : Optional.of(hours.get(hours.size() / 2));
    }
}
