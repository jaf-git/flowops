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
import com.flowops.analyser.domain.WorkName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProcessAnalyser implements Analyser {
    private static final int ENOUGH_RUNS_TO_TRUST_AN_ORDER = 6;
    private static final double ORDER_CONFIDENCE_FLOOR = 0.85;

    @Override
    public AnalyserStage stage() {
        return AnalyserStage.CORRELATE;
    }

    @Override
    public String id() {
        return "S8_PROCESSES";
    }

    @Override
    public Category category() {
        return Category.YOUR_PROCESSES;
    }

    @Override
    public Report analyse(Snapshot snapshot) {
        Snapshot.Shapes shapes = snapshot.shapes();

        if (!shapes.ran()) {
            return Report.blocked(
                    id(),
                    0,
                    Precondition.unmet(
                            "work the clusterer could read",
                            "nothing in this window reached it — finished engagements are what it needs",
                            "Close the work and end the engagement"));
        }

        List<Finding> findings = new ArrayList<>();
        List<Absence> absences = new ArrayList<>();
        List<Clean> clean = new ArrayList<>();

        Map<String, String> names = WorkName.byShapeId(shapes, snapshot.nodes());

        for (Snapshot.Process process : shapes.processes()) {
            findings.add(discovered(process, shapes, names));
        }

        if (shapes.processes().isEmpty()) {
            clean.add(new Clean(
                    "no_repeated_process",
                    ("%d units of work clustered into %d kinds; none of them repeats as a whole process yet. "
                                    + "A process needs the same shape of work to run more than twice.")
                            .formatted(shapes.clustered(), shapes.kinds().size())));
        }

        alwaysStateTheBlindSpot(shapes, absences);
        noteWhatChurnRemoved(shapes, absences);

        return new Report(
                id(),
                shapes.clustered(),
                findings,
                absences,
                clean,
                List.of(Precondition.met(
                        "work the clusterer could read",
                        "%d units of work, %d kinds"
                                .formatted(shapes.clustered(), shapes.kinds().size()))));
    }

    private Finding discovered(Snapshot.Process process, Snapshot.Shapes shapes, Map<String, String> names) {
        List<String> because = new ArrayList<>();
        because.add("Run %d times, across %d engagements."
                .formatted(process.runs(), process.jobIds().size()));

        List<String> stepNames = process.order().stream()
                .map(step -> WorkName.named(step, names))
                .toList();

        if (process.orderReliable()) {
            because.add("The steps run in this order: %s.".formatted(String.join(" → ", stepNames)));
        } else {
            because.add("The steps are %s. %s".formatted(String.join(", ", stepNames), whyTheOrderIsWithheld(process)));
        }
        because.add("Nothing has been created from this. Turning a discovered shape into a process "
                + "template is a person's decision.");

        return new Finding(
                id(),
                "repeated_process",
                SubjectKind.SHAPE,
                String.join("+", process.steps()),
                category(),
                "The same %d-step process has run %d times"
                        .formatted(process.steps().size(), process.runs()),
                because,
                Map.of(
                        Finding.EvidenceKind.JOB, process.jobIds(),
                        Finding.EvidenceKind.NODE, nodesBehind(process, shapes)),
                Severity.MEDIUM,
                process.certainty() >= 0.8 ? Confidence.HIGH : Confidence.MEDIUM,
                process.runs(),
                null,
                "Write it down as a process");
    }

    private static String whyTheOrderIsWithheld(Snapshot.Process process) {
        boolean tooFewRuns = process.runs() < ENOUGH_RUNS_TO_TRUST_AN_ORDER;
        boolean tooVaried = process.orderConfidence() < ORDER_CONFIDENCE_FLOOR;

        if (tooFewRuns && tooVaried) {
            return ("The order is not shown: %d runs is not enough to tell a real sequence from the order things "
                            + "happened to be marked in, and the runs there are did not agree on one.")
                    .formatted(process.runs());
        }
        if (tooFewRuns) {
            return ("The order is not shown, because %d runs is not enough to tell a real sequence from the "
                            + "order things happened to be marked in.")
                    .formatted(process.runs());
        }
        return ("The order is not shown: across %d runs the steps did not happen in a consistent enough "
                        + "sequence to call one. Running it again will not settle it — the runs genuinely differ.")
                .formatted(process.runs());
    }

    private static List<String> nodesBehind(Snapshot.Process process, Snapshot.Shapes shapes) {
        return shapes.kinds().stream()
                .filter(kind -> process.steps().contains(kind.id()))
                .flatMap(kind -> kind.nodeIds().stream())
                .distinct()
                .toList();
    }

    /**
     * States the in-department blind spot, and states it as it now is rather than as it was.
     *
     * <p><b>This text was a false statement about the product until an activity could be named.</b>
     * It said in-department processes were undetectable, that the cause was structural, and that no
     * amount of data would fix it. All three were true of a step keyed on the kind of work. None is
     * true of a step keyed on the activity somebody chose: S3's document collection is six steps by
     * one person in one department, and it is found.
     *
     * <p>So the blind spot is now conditional on adoption rather than on structure, and the
     * condition is read from the shapes themselves — a kind carries the activity name it was built
     * from, so the share of kinds that name one is the share of this workspace that has left the
     * blind spot. Nothing is asserted here that the snapshot cannot show.
     */
    private void alwaysStateTheBlindSpot(Snapshot.Shapes shapes, List<Absence> absences) {
        long named = shapes.kinds().stream()
                .filter(kind ->
                        kind.activityName() != null && !kind.activityName().isBlank())
                .count();
        long unnamed = shapes.kinds().size() - named;

        String detail;
        if (named == 0) {
            detail = "Processes where every step is done by the same team are not detected while nobody "
                    + "names the activity. A step falls back to the kind of work, so a three-step "
                    + "editorial run where every step is WRITER collapses to one step before clustering "
                    + "begins — and a one-step shape is not a process. Naming the activity on each mark "
                    + "separates them; nothing else here will.";
        } else if (unnamed == 0) {
            detail = ("Every one of the %d kinds of work here names an activity, so a process performed "
                            + "entirely inside one team is as detectable as any other. This used to be a "
                            + "structural blind spot and is no longer one.")
                    .formatted(named);
        } else {
            detail = ("%d of %d kinds of work here name an activity and %d fall back to the kind of work. "
                            + "Where the activity is named, a process performed entirely inside one team is "
                            + "detected like any other; where it is not, every step of such a process still "
                            + "collapses into one and the process cannot be seen. The blind spot is now a "
                            + "question of whether people name their work, not of how the work is arranged.")
                    .formatted(named, shapes.kinds().size(), unnamed);
        }

        absences.add(new Absence("intra_department_process", detail, false));
    }

    /**
     * Says what churn removed, without claiming a reason for all of it that is true of some of it.
     *
     * <p>{@code Churn.excludedFrom} is two rules — repeated marks on one thing, and everything in an
     * engagement that has not finished — and the analyser is given their sum. The old wording named
     * only repetition, which in a consultancy whose placements run four to eight months described
     * three of nineteen units and misdescribed sixteen.
     */
    private void noteWhatChurnRemoved(Snapshot.Shapes shapes, List<Absence> absences) {
        if (shapes.excludedAsChurn() > 0) {
            absences.add(new Absence(
                    "removed_as_churn",
                    ("%d units of work were removed before clustering — repeated marks on the same thing, "
                                    + "and work in engagements that have not finished — so every figure here "
                                    + "is out of the %d that remained. Work in an engagement still running is "
                                    + "not lost; it counts once that engagement ends.")
                            .formatted(shapes.excludedAsChurn(), shapes.clustered()),
                    false));
        }
    }
}
