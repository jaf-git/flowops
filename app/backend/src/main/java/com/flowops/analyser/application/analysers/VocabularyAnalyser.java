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
import com.flowops.analyser.domain.StrandedTemplates;
import com.flowops.analyser.domain.SubjectKind;
import com.flowops.analyser.domain.TitleKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class VocabularyAnalyser implements Analyser {
    private static final int NAMES_BEFORE_ITS_DRIFT = 3;

    private static final int KINDS_BEFORE_ITS_COLLAPSE = 2;

    @Override
    public AnalyserStage stage() {
        return AnalyserStage.DETECT;
    }

    @Override
    public String id() {
        return "S2_VOCABULARY";
    }

    @Override
    public Category category() {
        return Category.YOUR_LIBRARY;
    }

    @Override
    public Report analyse(Snapshot snapshot) {
        List<Named> named = new ArrayList<>();
        snapshot.templates().stream()
                .filter(template -> template.workType() != null && TitleKey.of(template.title()) != null)
                .forEach(template -> named.add(new Named(
                        template.workType(),
                        TitleKey.of(template.title()),
                        Finding.EvidenceKind.TEMPLATE,
                        template.id())));
        snapshot.nodes().stream()
                .filter(node -> node.workType() != null && TitleKey.of(node.title()) != null)
                .forEach(node -> named.add(
                        new Named(node.workType(), TitleKey.of(node.title()), Finding.EvidenceKind.NODE, node.id())));

        if (named.isEmpty()) {
            return Report.blocked(
                    id(),
                    snapshot.templates().size() + snapshot.nodes().size(),
                    Precondition.unmet(
                            "work that somebody has named",
                            "nothing in this window carries both a governed kind of work and a name",
                            "Name a piece of work when you mark it"));
        }

        List<Finding> findings = new ArrayList<>();
        List<Clean> clean = new ArrayList<>();

        Map<String, Set<String>> namesPerKind = group(named, Named::workType, Named::name);
        Map<String, Set<String>> kindsPerName = group(named, Named::name, Named::workType);

        drift(namesPerKind, named)
                .ifPresentOrElse(
                        findings::add,
                        () -> clean.add(new Clean(
                                "one_name_per_kind_of_work",
                                "%d kinds of work checked; none is called more than %d different things."
                                        .formatted(namesPerKind.size(), NAMES_BEFORE_ITS_DRIFT))));

        collapse(kindsPerName, named).ifPresent(findings::add);
        findings.addAll(strandedByAMerge(snapshot));

        return new Report(
                id(),
                named.size(),
                findings,
                List.of(new Absence(
                        "names_that_differ_only_in_meaning",
                        "Names are compared as text. \"caption set\" and \"post copy\" are one kind of work "
                                + "called two things and share no characters, so this count is a floor rather "
                                + "than a total — only meaning joins them.",
                        false)),
                clean,
                List.of(Precondition.met("work that somebody has named", "%d named things".formatted(named.size()))));
    }

    private java.util.Optional<Finding> drift(Map<String, Set<String>> namesPerKind, List<Named> named) {
        Map<String, Set<String>> drifting = namesPerKind.entrySet().stream()
                .filter(entry -> entry.getValue().size() > NAMES_BEFORE_ITS_DRIFT)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        if (drifting.isEmpty()) {
            return java.util.Optional.empty();
        }

        List<String> because = new ArrayList<>();
        drifting.forEach((kind, names) -> because.add(
                "%s is called %d different things: %s.".formatted(kind, names.size(), String.join(", ", names))));
        because.add("Every count of that work is divided between those names, so each one looks smaller "
                + "than the work actually is.");

        return java.util.Optional.of(new Finding(
                id(),
                "one_kind_of_work_many_names",
                SubjectKind.WORK_TYPE,
                drifting.keySet().iterator().next(),
                category(),
                "%d kinds of work are each called several different things".formatted(drifting.size()),
                because,
                evidenceFor(named, entry -> drifting.containsKey(entry.workType())),
                Severity.MEDIUM,
                Confidence.HIGH,
                drifting.size(),
                namesPerKind.size(),
                "Agree on one word for each"));
    }

    private java.util.Optional<Finding> collapse(Map<String, Set<String>> kindsPerName, List<Named> named) {
        Map<String, Set<String>> collapsed = kindsPerName.entrySet().stream()
                .filter(entry -> entry.getValue().size() > KINDS_BEFORE_ITS_COLLAPSE)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        if (collapsed.isEmpty()) {
            return java.util.Optional.empty();
        }

        List<String> because = new ArrayList<>();
        collapsed.forEach((name, kinds) -> because.add("\"%s\" is used for %d different kinds of work: %s."
                .formatted(name, kinds.size(), String.join(", ", kinds))));
        because.add("A name that covers several kinds of work stops telling them apart, so anything "
                + "matched on it merges figures nobody would have grouped by hand.");

        return java.util.Optional.of(new Finding(
                id(),
                "one_name_many_kinds_of_work",
                SubjectKind.WORKSPACE,
                "vocabulary",
                category(),
                "%d names are each used for several different kinds of work".formatted(collapsed.size()),
                because,
                evidenceFor(named, entry -> collapsed.containsKey(entry.name())),
                Severity.MEDIUM,
                Confidence.HIGH,
                collapsed.size(),
                kindsPerName.size(),
                "Split the name"));
    }

    private static Map<Finding.EvidenceKind, List<String>> evidenceFor(
            List<Named> named, java.util.function.Predicate<Named> keep) {
        Map<Finding.EvidenceKind, List<String>> evidence = new LinkedHashMap<>();
        named.stream().filter(keep).forEach(entry -> evidence.computeIfAbsent(entry.kind(), kind -> new ArrayList<>())
                .add(entry.id()));
        return Map.copyOf(evidence);
    }

    private record Named(String workType, String name, Finding.EvidenceKind kind, String id) {}

    /**
     * Approved templates still carrying the name of an activity somebody merged away.
     *
     * <p>The merge raises this the moment it happens, because the person who has just decided that
     * two names were one activity is the one who can judge whether the templates still hold. This
     * raises it again for as long as it is true — which is what catches a merge made in a workspace
     * that had no completed analysis for the first one to attach to.
     *
     * <p>Same key both times, so the second sighting is the same finding rather than a second one: a
     * dismissal made once stays made, and {@code times_seen} climbs instead of the queue filling with
     * copies.
     */
    private static List<Finding> strandedByAMerge(Snapshot snapshot) {
        List<Finding> stranded = new ArrayList<>();

        for (Snapshot.MergedActivity merged : snapshot.mergedActivities()) {
            List<StrandedTemplates.Stranded> left = snapshot.templates().stream()
                    .filter(template -> "APPROVED".equals(template.status()))
                    .filter(template ->
                            template.title() != null && template.title().equalsIgnoreCase(merged.name()))
                    .map(template ->
                            new StrandedTemplates.Stranded(template.id(), template.title(), template.timesUsed()))
                    .toList();

            StrandedTemplates found = new StrandedTemplates(merged.name(), merged.survivingName(), left);
            if (!found.isEmpty()) {
                stranded.add(found.asFinding());
            }
        }

        return stranded;
    }

    private static Map<String, Set<String>> group(
            List<Named> named,
            java.util.function.Function<Named, String> by,
            java.util.function.Function<Named, String> of) {
        return named.stream()
                .collect(Collectors.groupingBy(
                        by, LinkedHashMap::new, Collectors.mapping(of, Collectors.toCollection(LinkedHashSet::new))));
    }
}
