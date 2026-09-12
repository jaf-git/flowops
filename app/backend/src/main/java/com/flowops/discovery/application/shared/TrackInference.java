package com.flowops.discovery.application.shared;

import com.flowops.discovery.application.shared.port.PersonRolePort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.Direction;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.Track;
import com.flowops.discovery.domain.model.TrackKey;
import com.flowops.discovery.domain.model.WorkNode;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TrackInference {
    private final WorkGraphPort graph;
    private final PersonRolePort roles;
    private final Clock clock;

    public TrackInference(WorkGraphPort graph, PersonRolePort roles, Clock clock) {
        this.graph = graph;
        this.roles = roles;
        this.clock = clock;
    }

    public Optional<Track> place(WorkNode node, JobId job) {
        if (!node.direction().canJoinATrack()) {
            return Optional.empty();
        }
        if (namesSomebodyWhoIsGone(node)) {
            return Optional.empty();
        }

        Optional<Track> running = alreadyRunning(node, job);
        if (running.isPresent()) {
            Track track = running.get();
            track.nodeAdded(clock.instant());
            graph.save(track);
            return running;
        }

        Track opened = Track.openedBy(graph.nextTrackId(), node, keyFor(node), clock.instant());
        graph.save(opened);
        return Optional.of(opened);
    }

    private boolean namesSomebodyWhoIsGone(WorkNode node) {
        if (node.direction() != Direction.REQUEST) {
            return false;
        }
        return node.performerId().filter(named -> !roles.isActiveMember(named)).isPresent();
    }

    private Optional<Track> alreadyRunning(WorkNode node, JobId job) {
        if (node.direction() == Direction.COMPLETION) {
            return graph.findOpenTrackForPerformer(job, node.performerId().orElse(node.creatorId()));
        }
        return graph.findOpenTrack(job, keyFor(node));
    }

    private TrackKey keyFor(WorkNode node) {
        UUID performer = node.performerId().orElse(node.creatorId());
        UUID performerRole =
                node.performerRoleId().or(() -> roles.roleOf(performer)).orElse(null);
        UUID creatorRole =
                node.creatorRoleId().or(() -> roles.roleOf(node.creatorId())).orElse(null);

        if (creatorRole == null || performerRole == null) {
            return TrackKey.byPerformerAlone(performer);
        }

        return TrackKey.byRolePair(creatorRole, performerRole, performer);
    }
}
