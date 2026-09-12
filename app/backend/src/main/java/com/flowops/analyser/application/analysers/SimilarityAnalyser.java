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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SimilarityAnalyser implements Analyser {
    private static final int ENGAGEMENTS_BEFORE_ITS_SHARED = 2;

    @Override
    public AnalyserStage stage() {
        return AnalyserStage.CORRELATE;
    }

    @Override
    public String id() {
        return "S7_SIMILARITY";
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

        List<Snapshot.Shape> shared = shapes.kinds().stream()
                .filter(kind -> kind.jobIds().size() >= ENGAGEMENTS_BEFORE_ITS_SHARED)
                .toList();

        List<Finding> findings = new ArrayList<>();
        List<Absence> absences = new ArrayList<>();
        List<Clean> clean = new ArrayList<>();

        if (shared.isEmpty()) {
            absences.add(new Absence(
                    "no_shape_crosses_an_engagement",
                    ("%d kinds of work were found and none of them appears in more than one engagement. "
                                    + "Work that never recurs across engagements cannot form a repeated process, "
                                    + "so this is why no process was found rather than a fault in finding one.")
                            .formatted(shapes.kinds().size()),
                    true));
        } else {
            findings.add(sharedShapes(shared, shapes));
        }

        subprocesses(shapes).ifPresent(clean::add);

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

    private Finding sharedShapes(List<Snapshot.Shape> shared, Snapshot.Shapes shapes) {
        List<String> because = new ArrayList<>();
        because.add(shared.stream()
                        .limit(5)
                        .map(kind -> "%s (%d engagements)"
                                .formatted(kind.workType(), kind.jobIds().size()))
                        .collect(java.util.stream.Collectors.joining(", "))
                + (shared.size() > 5 ? " and %d more.".formatted(shared.size() - 5) : "."));
        because.add("These are the kinds of work your engagements have in common. Whether they run in "
                + "the same order is a separate question, and the one S8 answers.");

        return new Finding(
                id(),
                "work_shared_across_engagements",
                SubjectKind.SHAPE,
                "shared",
                category(),
                "%d kinds of work recur across more than one engagement".formatted(shared.size()),
                because,
                Map.of(
                        Finding.EvidenceKind.NODE,
                        shared.stream()
                                .flatMap(kind -> kind.nodeIds().stream())
                                .distinct()
                                .toList()),
                Severity.LOW,
                Confidence.HIGH,
                shared.size(),
                shapes.kinds().size(),
                "Look at whether they run in the same order");
    }

    private java.util.Optional<Clean> subprocesses(Snapshot.Shapes shapes) {
        long count = shapes.kinds().stream().filter(Snapshot.Shape::subprocess).count();
        if (count == 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Clean(
                "work_organised_into_side_conversations",
                ("%d kinds of work happen in a direct-message stream of their own rather than in the main "
                                + "channel. That is a handoff people have organised, and it is counted separately "
                                + "so it does not merge with the work it supports.")
                        .formatted(count)));
    }
}
