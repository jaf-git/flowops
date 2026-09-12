package com.flowops.analyser.application.analysers;

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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@org.springframework.stereotype.Component
public class LibraryCoverage implements Analyser {
    @Override
    public AnalyserStage stage() {
        return AnalyserStage.OBSERVE;
    }

    @Override
    public String id() {
        return "S6_LIBRARY";
    }

    @Override
    public Category category() {
        return Category.YOUR_LIBRARY;
    }

    private static final int NAMED_IN_THE_REASON = 5;

    @Override
    public Report analyse(Snapshot snapshot) {
        List<Snapshot.Template> approved = snapshot.templates();

        Set<String> workKinds = workKindsWithBrackets(snapshot);

        if (approved.isEmpty()) {
            return new Report(
                    id(),
                    0,
                    ungoverned(workKinds, snapshot).map(List::of).orElseGet(List::of),
                    List.of(),
                    List.of(),
                    List.of(unmetLibrary()));
        }

        List<Finding> findings = new ArrayList<>();
        List<Clean> clean = new ArrayList<>();

        neverMatched(approved)
                .ifPresentOrElse(
                        findings::add,
                        () -> clean.add(new Clean(
                                "every_template_matched",
                                "%d approved templates checked; every one has matched real work."
                                        .formatted(approved.size()))));

        Set<String> workKindsInUse = workKinds;
        ungoverned(workKindsInUse, snapshot).ifPresent(findings::add);
        uncovered(approved, workKindsInUse, snapshot).ifPresentOrElse(findings::add, () -> {
            if (!workKindsInUse.isEmpty()) {
                clean.add(new Clean(
                        "every_work_kind_covered",
                        "All %d kinds of work seen in this window have a template.".formatted(workKindsInUse.size())));
            }
        });

        return new Report(
                id(),
                approved.size(),
                findings,
                List.of(),
                clean,
                List.of(Precondition.met("an approved template to check", "%d of them".formatted(approved.size()))));
    }

    private java.util.Optional<Finding> neverMatched(List<Snapshot.Template> approved) {
        List<Snapshot.Template> dead =
                approved.stream().filter(template -> !template.hasMatchedWork()).toList();

        if (dead.isEmpty()) {
            return java.util.Optional.empty();
        }

        List<String> because = new ArrayList<>();
        because.add(dead.stream()
                        .limit(NAMED_IN_THE_REASON)
                        .map(Snapshot.Template::title)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining(", "))
                + (dead.size() > NAMED_IN_THE_REASON
                        ? " and %d more.".formatted(dead.size() - NAMED_IN_THE_REASON)
                        : "."));
        because.add("A template nobody has ever matched is either describing work you stopped doing, "
                + "or is worded so that nothing reaches it.");
        because.add("Nothing here has been retired. Retiring a template reaches every screen that reads "
                + "the library, and that is a person's decision.");

        return java.util.Optional.of(new Finding(
                id(),
                "never_matched_template",
                SubjectKind.WORKSPACE,
                "library",
                category(),
                "%d of %d approved templates have never been used to create a task"
                        .formatted(dead.size(), approved.size()),
                because,
                Map.of(
                        Finding.EvidenceKind.TEMPLATE,
                        dead.stream().map(Snapshot.Template::id).toList()),
                Severity.MEDIUM,
                Confidence.HIGH,
                dead.size(),
                approved.size(),
                "Review the library"));
    }

    private java.util.Optional<Finding> uncovered(
            List<Snapshot.Template> approved, Set<String> workKindsInUse, Snapshot snapshot) {
        Set<String> covered = approved.stream()
                .map(Snapshot.Template::workType)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<String> missing =
                workKindsInUse.stream().filter(kind -> !covered.contains(kind)).toList();

        if (missing.isEmpty()) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(new Finding(
                id(),
                "work_kind_without_a_template",
                SubjectKind.WORKSPACE,
                "library",
                category(),
                "%d of %d kinds of work you actually do have no template"
                        .formatted(missing.size(), workKindsInUse.size()),
                List.of(
                        String.join(", ", missing) + ".",
                        "These are kinds of work with real brackets in this window and nothing in the "
                                + "library describing them."),
                Map.of(
                        Finding.EvidenceKind.BRACKET,
                        snapshot.brackets().stream()
                                .filter(bracket -> missing.contains(bracket.workType()))
                                .map(Snapshot.Bracket::id)
                                .toList()),
                Severity.MEDIUM,
                Confidence.HIGH,
                missing.size(),
                workKindsInUse.size(),
                "Write a template"));
    }

    private static Precondition unmetLibrary() {
        return Precondition.unmet(
                "an approved template to check", "nothing in the library is approved yet", "Approve a template");
    }

    private java.util.Optional<Finding> ungoverned(Set<String> workKindsInUse, Snapshot snapshot) {
        if (snapshot.governedWorkTypes().isEmpty()) {
            return java.util.Optional.empty();
        }

        Set<String> governed = Set.copyOf(snapshot.governedWorkTypes());
        List<String> outside =
                workKindsInUse.stream().filter(kind -> !governed.contains(kind)).toList();

        if (outside.isEmpty()) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(new Finding(
                id(),
                "work_kind_outside_the_vocabulary",
                SubjectKind.WORKSPACE,
                "library",
                category(),
                "%d %s of work you do %s not in the agreed vocabulary"
                        .formatted(
                                outside.size(),
                                outside.size() == 1 ? "kind" : "kinds",
                                outside.size() == 1 ? "is" : "are"),
                List.of(
                        String.join(", ", outside) + ".",
                        "Work can be marked with any kind of work, and a template can only carry one the "
                                + "workspace has agreed on. So these are being done and no template for them "
                                + "can be saved.",
                        "Either add them to the vocabulary or mark that work under a kind that is in it."),
                Map.of(
                        Finding.EvidenceKind.BRACKET,
                        snapshot.brackets().stream()
                                .filter(bracket -> outside.contains(bracket.workType()))
                                .map(Snapshot.Bracket::id)
                                .toList()),
                Severity.HIGH,
                Confidence.HIGH,
                outside.size(),
                workKindsInUse.size(),
                "Agree on the word"));
    }

    private static Set<String> workKindsWithBrackets(Snapshot snapshot) {
        return snapshot.brackets().stream()
                .map(Snapshot.Bracket::workType)
                .filter(Objects::nonNull)
                .filter(workType -> !workType.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
