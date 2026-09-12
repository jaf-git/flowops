package com.flowops.discovery.application.clustering;

import com.flowops.discovery.application.shared.port.DiscoveryThresholdPort;
import com.flowops.discovery.application.shared.port.TrackClosurePort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.Track;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TrackClustering {
    private final DiscoveredWorkPort discoveredWork;
    private final TrackClosurePort closure;
    private final WorkGraphPort graph;
    private final DiscoveryThresholdPort thresholds;

    public TrackClustering(
            DiscoveredWorkPort discoveredWork,
            TrackClosurePort closure,
            WorkGraphPort graph,
            DiscoveryThresholdPort thresholds) {
        this.discoveredWork = discoveredWork;
        this.closure = closure;
        this.graph = graph;
        this.thresholds = thresholds;
    }

    public record Cluster(TrackShape shape, List<Track> threads) {
        public Cluster {
            threads = List.copyOf(threads);
        }

        public int completedThreads() {
            return threads.size();
        }
    }

    public List<Cluster> clusters() {
        int floor = thresholds.thresholds().tracksToFormCandidate();

        Map<TrackShape, List<Track>> byShape = new LinkedHashMap<>();
        for (JobId engagement : discoveredWork.engagementsWithClosedWork()) {
            for (Track thread : closure.closedQualifyingTracks(engagement)) {
                TrackShape.of(thread, graph.nodesOf(thread.id()))
                        .ifPresent(shape -> byShape.computeIfAbsent(shape, unused -> new ArrayList<>())
                                .add(thread));
            }
        }

        List<Cluster> clusters = new ArrayList<>();
        byShape.forEach((shape, threads) -> {
            if (threads.size() >= floor) {
                clusters.add(new Cluster(shape, threads));
            }
        });
        clusters.sort((left, right) -> Integer.compare(right.completedThreads(), left.completedThreads()));
        return clusters;
    }
}
