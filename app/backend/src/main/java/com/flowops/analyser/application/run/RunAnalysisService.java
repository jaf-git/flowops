package com.flowops.analyser.application.run;

import com.flowops.analyser.application.shared.port.AnalysisJournalPort;
import com.flowops.analyser.application.shared.port.DismissalPort;
import com.flowops.analyser.application.shared.port.FindingStorePort;
import com.flowops.analyser.application.shared.port.SnapshotPort;
import com.flowops.analyser.domain.Analyser;
import com.flowops.analyser.domain.AnalyserStage;
import com.flowops.analyser.domain.Finding;
import com.flowops.analyser.domain.FindingContext;
import com.flowops.analyser.domain.FindingFingerprint;
import com.flowops.analyser.domain.Lifecycle;
import com.flowops.analyser.domain.LifecycleRule;
import com.flowops.analyser.domain.Report;
import com.flowops.analyser.domain.Snapshot;
import com.flowops.analyser.domain.SubjectNaming;
import com.flowops.analyser.domain.WorkName;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunAnalysisService implements RunAnalysisUseCase {
    private static final Logger LOG = LoggerFactory.getLogger(RunAnalysisService.class);

    private static final int DEFAULT_WINDOW_DAYS = 90;

    private final List<Analyser> analysers;
    private final SnapshotPort snapshots;
    private final AnalysisJournalPort journal;
    private final FindingStorePort findings;
    private final DismissalPort dismissals;
    private final LifecycleRule lifecycleRule;
    private final Clock clock;
    private final java.util.Map<String, AnalyserStage> stages;

    public RunAnalysisService(
            List<Analyser> analysers,
            SnapshotPort snapshots,
            AnalysisJournalPort journal,
            FindingStorePort findings,
            DismissalPort dismissals,
            Clock clock,
            @Value("${flowops.analyser.worsening-factor:1.5}") double worseningFactor,
            @Value("${flowops.analyser.share-drift:0.05}") double shareDrift) {
        this.analysers = List.copyOf(analysers);
        this.snapshots = snapshots;
        this.journal = journal;
        this.findings = findings;
        this.dismissals = dismissals;
        this.clock = clock;
        this.lifecycleRule = new LifecycleRule(worseningFactor, shareDrift);
        this.stages = this.analysers.stream().collect(java.util.stream.Collectors.toMap(Analyser::id, Analyser::stage));
    }

    private AnalyserStage stageOf(Finding finding) {
        AnalyserStage stage = stages.get(finding.analyser());
        if (stage == null) {
            throw new IllegalStateException("no stage declared for analyser " + finding.analyser());
        }
        return stage;
    }

    @Override
    @Transactional
    public Summary execute(RunAnalysis command) {
        Instant now = clock.instant();
        int days = command.windowDays() == null ? DEFAULT_WINDOW_DAYS : command.windowDays();
        if (days <= 0) {
            throw new IllegalArgumentException("a window of " + days + " days holds nothing to analyse");
        }
        Instant from = now.minus(Duration.ofDays(days));

        Snapshot snapshot = snapshots.readWindow(from, now);
        UUID runId = journal.open(from, now, now);

        List<Report> reports = new ArrayList<>();
        List<AnalyserOutcome> outcomes = new ArrayList<>();
        List<String> saidNothing = new ArrayList<>();

        for (Analyser analyser : analysers) {
            try {
                Report report = analyser.analyse(snapshot);

                if (!report.saysSomething()) {
                    saidNothing.add(analyser.id());
                }

                reports.add(report);
                journal.record(runId, report, silenceOf(report));
                outcomes.add(outcomeOf(report, silenceOf(report)));
            } catch (RuntimeException failure) {
                LOG.warn("analyser {} failed during run {}", analyser.id(), runId, failure);
                Report empty = new Report(analyser.id(), 0, List.of(), List.of(), List.of(), List.of());
                journal.record(runId, empty, describe(failure));
                outcomes.add(outcomeOf(empty, describe(failure)));
            }
        }

        storeFindings(runId, reports, snapshot, now);

        if (saidNothing.isEmpty()) {
            journal.finish(runId, clock.instant());
        } else {
            journal.fail(runId, clock.instant(), "said nothing at all: " + String.join(", ", saidNothing));
        }

        return new Summary(runId, from, now, List.copyOf(outcomes));
    }

    private static String silenceOf(Report report) {
        return report.saysSomething()
                ? null
                : "emitted no finding, no absence and no clean result -- an analyser must say at least one";
    }

    private void storeFindings(UUID runId, List<Report> reports, Snapshot snapshot, Instant now) {
        List<Finding> all =
                reports.stream().flatMap(report -> report.findings().stream()).toList();
        if (all.isEmpty()) {
            return;
        }

        Map<String, FindingStorePort.PreviousSighting> before =
                findings.previousSightingsOf(all.stream().map(Finding::key).toList());

        Map<String, DismissalPort.InForce> saidNo = dismissals.current();

        Map<String, String> names = WorkName.byShapeId(snapshot.shapes(), snapshot.nodes());

        for (Finding finding : all) {
            FindingStorePort.PreviousSighting previous = before.get(finding.key());

            Lifecycle lifecycle = lifecycleOf(finding, previous, saidNo.get(finding.key()));

            Instant firstSeen = previous == null ? now : previous.firstSeenAt();

            int timesSeen = previous == null || lifecycle == Lifecycle.WORSENING ? 1 : previous.timesSeen() + 1;

            findings.store(
                    runId,
                    finding,
                    new FindingStorePort.Presentation(
                            SubjectNaming.of(finding, names),
                            FindingContext.of(finding, snapshot, names),
                            stageOf(finding)),
                    lifecycle,
                    firstSeen,
                    timesSeen,
                    now);
        }
    }

    private Lifecycle lifecycleOf(
            Finding finding, FindingStorePort.PreviousSighting previous, DismissalPort.InForce dismissal) {
        LifecycleRule.Sighting current = new LifecycleRule.Sighting(finding.reach(), finding.reachOf());

        if (dismissal != null) {
            FindingFingerprint atDismissal = FindingFingerprint.parse(dismissal.fingerprint());
            return atDismissal.sameCharacterAs(FindingFingerprint.of(finding))
                    ? lifecycleRule.decide(atDismissal.sighting(), current, true)
                    : Lifecycle.WORSENING;
        }

        return lifecycleRule.decide(
                previous == null ? null : new LifecycleRule.Sighting(previous.reach(), previous.reachOf()),
                current,
                false);
    }

    private static AnalyserOutcome outcomeOf(Report report, String failure) {
        return new AnalyserOutcome(
                report.analyser(),
                report.read(),
                report.findings().size(),
                report.absences().size(),
                (int) report.preconditions().stream()
                        .filter(precondition -> !precondition.met())
                        .count(),
                failure);
    }

    private static String describe(RuntimeException failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
