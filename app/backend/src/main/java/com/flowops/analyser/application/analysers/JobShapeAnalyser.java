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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class JobShapeAnalyser implements Analyser {
    private static final int ENOUGH_JOBS_TO_COMPARE = 5;

    private static final double TIMES_THE_USUAL_SIZE = 3.0;

    @Override
    public AnalyserStage stage() {
        return AnalyserStage.DETECT;
    }

    @Override
    public String id() {
        return "S5_JOB_SHAPE";
    }

    @Override
    public Category category() {
        return Category.YOUR_ENGAGEMENTS;
    }

    @Override
    public Report analyse(Snapshot snapshot) {
        List<Snapshot.Job> jobs = snapshot.jobs();

        if (jobs.size() < ENOUGH_JOBS_TO_COMPARE) {
            return Report.blocked(
                    id(),
                    jobs.size(),
                    Precondition.unmet(
                            "at least %d engagements to compare against each other".formatted(ENOUGH_JOBS_TO_COMPARE),
                            "%d in this window".formatted(jobs.size()),
                            "Open and close more engagements"));
        }

        Map<String, List<Snapshot.Bracket>> workPerJob = snapshot.brackets().stream()
                .filter(bracket -> bracket.jobId() != null)
                .collect(Collectors.groupingBy(Snapshot.Bracket::jobId, LinkedHashMap::new, Collectors.toList()));

        List<Finding> findings = new ArrayList<>();
        List<Absence> absences = new ArrayList<>();
        List<Clean> clean = new ArrayList<>();

        stalled(jobs, workPerJob).ifPresent(findings::add);
        unusuallyLarge(jobs, workPerJob).ifPresent(findings::add);

        if (findings.isEmpty()) {
            clean.add(new Clean(
                    "every_engagement_looks_ordinary",
                    "%d engagements checked; none is stalled or shaped unlike the others.".formatted(jobs.size())));
        }

        long unmeasurable =
                jobs.stream().filter(job -> !workPerJob.containsKey(job.id())).count();
        if (unmeasurable > 0) {
            absences.add(new Absence(
                    "engagement_with_no_recorded_work",
                    ("%d of %d engagements have no work recorded against them in this window, so their shape "
                                    + "cannot be compared with anything.")
                            .formatted(unmeasurable, jobs.size()),
                    false));
        }

        return new Report(
                id(),
                jobs.size(),
                findings,
                absences,
                clean,
                List.of(Precondition.met(
                        "at least %d engagements to compare against each other".formatted(ENOUGH_JOBS_TO_COMPARE),
                        "%d in this window".formatted(jobs.size()))));
    }

    private java.util.Optional<Finding> stalled(List<Snapshot.Job> jobs, Map<String, List<Snapshot.Bracket>> work) {
        List<Snapshot.Job> stalled = jobs.stream()
                .filter(job -> job.closedAt() == null)
                .filter(job -> {
                    List<Snapshot.Bracket> brackets = work.getOrDefault(job.id(), List.of());
                    return !brackets.isEmpty()
                            && brackets.stream().noneMatch(b -> b.outcome() == BracketOutcome.FINISHED);
                })
                .toList();

        if (stalled.isEmpty()) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(new Finding(
                id(),
                "engagement_producing_nothing",
                SubjectKind.WORKSPACE,
                "engagements",
                category(),
                stalled.size() == 1
                        ? "1 engagement is open with nothing finished in it"
                        : "%d engagements are open with nothing finished in them".formatted(stalled.size()),
                List.of(
                        stalled.stream().limit(5).map(Snapshot.Job::name).collect(Collectors.joining(", "))
                                + (stalled.size() > 5 ? " and %d more.".formatted(stalled.size() - 5) : "."),
                        "Work has been marked against each of these and none of it has been delivered or "
                                + "done. An engagement with work finishing inside it is running however long "
                                + "it has been open; this is the other shape."),
                Map.of(
                        Finding.EvidenceKind.JOB,
                        stalled.stream().map(Snapshot.Job::id).toList()),
                Severity.HIGH,
                Confidence.HIGH,
                stalled.size(),
                jobs.size(),
                "Look at what stopped"));
    }

    private java.util.Optional<Finding> unusuallyLarge(
            List<Snapshot.Job> jobs, Map<String, List<Snapshot.Bracket>> work) {
        List<Integer> sizes = jobs.stream()
                .map(job -> work.getOrDefault(job.id(), List.of()).size())
                .filter(size -> size > 0)
                .sorted()
                .toList();

        if (sizes.size() < ENOUGH_JOBS_TO_COMPARE) {
            return java.util.Optional.empty();
        }

        int median = sizes.get(sizes.size() / 2);
        double ceiling = Math.max(median * TIMES_THE_USUAL_SIZE, median + 2.0);

        List<Snapshot.Job> large = jobs.stream()
                .filter(job -> work.getOrDefault(job.id(), List.of()).size() > ceiling)
                .toList();

        if (large.isEmpty()) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(new Finding(
                id(),
                "engagement_much_larger_than_usual",
                SubjectKind.WORKSPACE,
                "engagements",
                category(),
                "%d engagements hold far more work than the rest".formatted(large.size()),
                List.of(
                        "The usual engagement here holds %d pieces of work; these hold more than %.0f."
                                .formatted(median, ceiling),
                        large.stream()
                                        .limit(5)
                                        .map(job -> "%s (%d)"
                                                .formatted(
                                                        job.name(),
                                                        work.getOrDefault(job.id(), List.of())
                                                                .size()))
                                        .collect(Collectors.joining(", "))
                                + ".",
                        "Compared against this workspace's own median rather than a fixed number — an "
                                + "agency whose engagements hold thirty pieces of work is not abnormal, it is "
                                + "that agency."),
                Map.of(
                        Finding.EvidenceKind.JOB,
                        large.stream().map(Snapshot.Job::id).toList()),
                Severity.LOW,
                Confidence.MEDIUM,
                large.size(),
                jobs.size(),
                "Check whether these are one engagement or several"));
    }
}
