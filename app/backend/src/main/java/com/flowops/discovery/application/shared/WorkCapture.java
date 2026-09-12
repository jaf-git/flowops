package com.flowops.discovery.application.shared;

import com.flowops.discovery.application.shared.exception.MessageNotMarkableException;
import com.flowops.discovery.application.shared.port.MarkableMessagePort;
import com.flowops.discovery.application.shared.port.PersonRolePort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.Direction;
import com.flowops.discovery.domain.enums.EvidenceOrigin;
import com.flowops.discovery.domain.enums.NodeKind;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.Track;
import com.flowops.discovery.domain.model.WorkNode;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WorkCapture {
    private final MarkableMessagePort messages;
    private final PersonRolePort roles;
    private final WorkGraphPort graph;
    private final TrackInference tracks;
    private final Clock clock;

    public WorkCapture(
            MarkableMessagePort messages,
            PersonRolePort roles,
            WorkGraphPort graph,
            TrackInference tracks,
            Clock clock) {
        this.messages = messages;
        this.roles = roles;
        this.graph = graph;
        this.tracks = tracks;
        this.clock = clock;
    }

    public Captured capture(
            UUID messageId, UUID callerId, JobId job, Direction direction, NodeKind kind, UUID performerId) {
        MarkableMessagePort.Words words =
                messages.read(messageId, callerId).orElseThrow(MessageNotMarkableException::new);

        Optional<WorkNode> alreadyMarked = graph.findMarkOf(messageId, job, performerId, kind);
        if (alreadyMarked.isPresent()) {
            WorkNode existing = alreadyMarked.get();
            Track placed = existing.track().flatMap(graph::findTrack).orElse(null);
            return new Captured(existing, placed, words.conversationId());
        }

        UUID author = words.authorId();

        WorkNode node = WorkNode.marked(
                WorkNodeId.of(UUID.randomUUID()),
                job,
                words.body(),
                author,
                roles.roleOf(author).orElse(null),
                kind,
                direction,
                clock.instant());

        if (performerId != null && direction == Direction.REQUEST) {
            node.requestedOf(performerId, roles.roleOf(performerId).orElse(null));
        } else if (direction == Direction.STANDALONE || direction == Direction.COMPLETION) {
            node.keptForSelf();
        }

        Optional<Track> track = tracks.place(node, job);
        track.ifPresent(placed -> node.placedIn(placed.id()));

        graph.save(node);

        graph.recordEvidence(node, messageId, EvidenceOrigin.PRIMARY);

        return new Captured(node, track.orElse(null), words.conversationId());
    }

    public record Captured(WorkNode node, Track track, UUID conversationId) {
        public Optional<Track> threadedInto() {
            return Optional.ofNullable(track);
        }
    }
}
