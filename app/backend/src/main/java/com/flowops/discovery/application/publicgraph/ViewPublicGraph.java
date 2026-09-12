package com.flowops.discovery.application.publicgraph;

import com.flowops.discovery.application.shared.port.ClientArtifactPort;
import com.flowops.discovery.application.shared.port.JobHeaderPort;
import com.flowops.discovery.application.shared.port.MyTrackPort;
import com.flowops.discovery.application.shared.port.MyWaitsPort;
import com.flowops.discovery.application.shared.port.MyWorkCountsPort;
import com.flowops.discovery.application.shared.port.PublicGraphPort;
import com.flowops.discovery.application.shared.port.TrackerRailPort;
import com.flowops.discovery.application.shared.port.WorkSearchPort;
import com.flowops.discovery.domain.model.JobId;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewPublicGraph {
    private static final int MOST_RESULTS_WORTH_SHOWING = 50;

    private static final DayOfWeek THE_WEEK_STARTS_ON = DayOfWeek.MONDAY;

    private final PublicGraphPort publicGraph;
    private final WorkSearchPort search;
    private final JobHeaderPort headers;
    private final ClientArtifactPort artifacts;
    private final TrackerRailPort rail;
    private final MyWorkCountsPort myWorkCounts;
    private final MyWaitsPort myWaits;
    private final MyTrackPort myTrack;
    private final Clock clock;

    public ViewPublicGraph(
            PublicGraphPort publicGraph,
            WorkSearchPort search,
            JobHeaderPort headers,
            ClientArtifactPort artifacts,
            TrackerRailPort rail,
            MyWorkCountsPort myWorkCounts,
            MyWaitsPort myWaits,
            MyTrackPort myTrack,
            Clock clock) {
        this.publicGraph = publicGraph;
        this.search = search;
        this.headers = headers;
        this.artifacts = artifacts;
        this.rail = rail;
        this.myWorkCounts = myWorkCounts;
        this.myWaits = myWaits;
        this.myTrack = myTrack;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TrackerRailPort.Lane> openWork() {
        return rail.openWork();
    }

    @Transactional(readOnly = true)
    public MyWorkCountsPort.MyWorkCounts myWork(UUID viewer) {
        return myWorkCounts.countsFor(viewer, startOfThisWeek());
    }

    @Transactional(readOnly = true)
    public List<MyWaitsPort.WaitOnMe> waitsOnMe(UUID viewer) {
        return myWaits.waitsOnMe(viewer);
    }

    @Transactional(readOnly = true)
    public List<MyTrackPort.TrackLine> myTrack(UUID viewer) {
        return myTrack.myTrack(viewer);
    }

    private Instant startOfThisWeek() {
        return LocalDate.now(clock)
                .with(TemporalAdjusters.previousOrSame(THE_WEEK_STARTS_ON))
                .atStartOfDay(clock.getZone())
                .toInstant();
    }

    @Transactional(readOnly = true)
    public List<ClientArtifactPort.Artifact> publishedBy(JobId job, UUID caller) {
        return artifacts.publishedBy(job, caller);
    }

    @Transactional(readOnly = true)
    public Optional<JobHeaderPort.JobHeader> headerOf(JobId job) {
        return headers.headerOf(job);
    }

    @Transactional(readOnly = true)
    public List<PublicGraphPort.PublicNode> shapeOf(JobId job) {
        return publicGraph.shapeOf(job);
    }

    @Transactional(readOnly = true)
    public Graph graphOf(JobId job) {
        return new Graph(publicGraph.shapeOf(job), publicGraph.edgesOf(job));
    }

    public record Graph(List<PublicGraphPort.PublicNode> nodes, List<PublicGraphPort.PublicEdge> edges) {}

    @Transactional(readOnly = true)
    public Found search(String query, UUID caller) {
        if (query == null || query.isBlank()) {
            return new Found(List.of(), 0);
        }

        String trimmed = query.trim();

        return new Found(
                search.readableMatches(trimmed, caller, MOST_RESULTS_WORTH_SHOWING),
                publicGraph.matchesBeyondReach(trimmed, caller));
    }

    public record Found(List<WorkSearchPort.Match> matches, int beyondReach) {}
}
