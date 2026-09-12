package com.flowops.discovery.application.analysis;

import com.flowops.discovery.domain.analysis.Correlator;
import com.flowops.discovery.domain.analysis.Detector;
import com.flowops.discovery.domain.analysis.Finding;
import com.flowops.discovery.domain.analysis.GraphWindow;
import com.flowops.discovery.domain.analysis.Recommendation;
import com.flowops.discovery.domain.analysis.Recommender;
import com.flowops.discovery.domain.analysis.Stage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunAnalysis {
    private static final Logger LOG = LoggerFactory.getLogger(RunAnalysis.class);

    private static final Duration DEFAULT_WINDOW = Duration.ofDays(30);

    private final List<Detector> detectors;
    private final List<Correlator> correlators;
    private final List<Recommender> recommenders;
    private final AnalysisGraphPort graph;
    private final AnalysisStorePort store;
    private final Phrasing phrasing;
    private final Clock clock;

    public RunAnalysis(
            List<Detector> detectors,
            List<Correlator> correlators,
            List<Recommender> recommenders,
            AnalysisGraphPort graph,
            AnalysisStorePort store,
            Phrasing phrasing,
            Clock clock) {
        this.detectors =
                detectors.stream().sorted(Comparator.comparing(Detector::stage)).toList();
        this.correlators = List.copyOf(correlators);
        this.recommenders = List.copyOf(recommenders);
        this.graph = graph;
        this.store = store;
        this.phrasing = phrasing;
        this.clock = clock;
    }

    @Transactional
    public AnalysisRun run() {
        Instant now = clock.instant();
        return run(now.minus(DEFAULT_WINDOW), now);
    }

    @Transactional
    public AnalysisRun run(Instant from, Instant to) {
        UUID runId = UUID.randomUUID();
        Instant startedAt = clock.instant();

        GraphWindow window = graph.read(from, to);

        store.openRun(runId, from, to, startedAt, window.all().size(), graph.waitCount(from, to));

        List<Finding> kept = new ArrayList<>();

        java.util.Map<Finding, UUID> storedAs = new java.util.HashMap<>();

        Stage reached = Stage.OBSERVE;

        for (Detector detector : detectors) {
            reached = detector.stage();

            try {
                for (Finding finding : detector.detect(window)) {
                    if (!finding.restsOnEnough(detector.sampleFloor())) {
                        continue;
                    }

                    Phrasing.Phrased phrased = phrasing.phrase(finding);
                    storedAs.put(finding, store.record(runId, finding, detector.stage(), phrased, startedAt));
                    kept.add(finding);
                }
            } catch (RuntimeException failed) {
                LOG.warn("detector {} did not complete: {}", detector.name(), failed.getMessage(), failed);
            }
        }

        reached = Stage.CORRELATE;
        List<Finding> correlations = new ArrayList<>();

        for (Correlator correlator : correlators) {
            try {
                for (Finding finding : correlator.correlate(window, List.copyOf(kept))) {
                    if (!finding.restsOnEnough(correlator.sampleFloor())) {
                        continue;
                    }

                    Phrasing.Phrased phrased = phrasing.phrase(finding);
                    storedAs.put(finding, store.record(runId, finding, Stage.CORRELATE, phrased, startedAt));
                    correlations.add(finding);
                }
            } catch (RuntimeException failed) {
                LOG.warn("correlator {} did not complete: {}", correlator.name(), failed.getMessage(), failed);
            }
        }

        kept.addAll(correlations);

        reached = Stage.RECOMMEND;
        int proposed = 0;

        for (Recommender recommender : recommenders) {
            try {
                for (Recommendation recommendation : recommender.recommend(List.copyOf(kept))) {
                    UUID evidence = storedAs.get(recommendation.evidence());

                    if (evidence == null) {
                        LOG.warn(
                                "recommender {} proposed something whose evidence was never stored; dropped",
                                recommender.name());
                        continue;
                    }

                    store.propose(runId, evidence, recommendation, startedAt);
                    proposed++;
                }
            } catch (RuntimeException failed) {
                LOG.warn("recommender {} did not complete: {}", recommender.name(), failed.getMessage(), failed);
            }
        }

        store.finishRun(runId, clock.instant(), reached);

        return new AnalysisRun(runId, from, to, window.all().size(), kept.size(), proposed, reached);
    }

    public record AnalysisRun(
            UUID id, Instant from, Instant to, int bracketsRead, int findings, int recommendations, Stage reached) {}
}
