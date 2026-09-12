package com.flowops.discovery.application.setsubject;

import com.flowops.discovery.application.markmessage.UnknownJobException;
import com.flowops.discovery.application.shared.TrackInference;
import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.exception.UnknownWorkNodeException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.SubjectSource;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.Track;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.WorkNode;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SetSubjectService implements SetSubjectUseCase {
    private final IdentifyCallerPort caller;
    private final WorkGraphPort graph;
    private final TrackInference tracks;

    public SetSubjectService(IdentifyCallerPort caller, WorkGraphPort graph, TrackInference tracks) {
        this.caller = caller;
        this.graph = graph;
        this.tracks = tracks;
    }

    @Override
    @Transactional
    public Corrected execute(SetSubject command) {
        caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        WorkNode node = graph.findNode(WorkNodeId.of(command.nodeId()))
                .orElseThrow(() -> new UnknownWorkNodeException(
                        "there is no unit of work with the identifier " + command.nodeId()));

        JobId chosen = JobId.of(command.jobId());
        graph.findJob(chosen).orElseThrow(() -> new UnknownJobException(command.jobId()));

        if (node.job().equals(chosen)) {
            return confirmed(node, chosen);
        }
        return corrected(node, chosen);
    }

    private Corrected confirmed(WorkNode node, JobId chosen) {
        node.subjectSetTo(chosen, SubjectSource.CONFIRMED);
        graph.save(node);

        Optional<Track> unchanged = node.track().flatMap(graph::findTrack);
        return new Corrected(
                node.id(),
                chosen,
                node.track(),
                unchanged.map(Track::isWeaklyKeyed).orElse(false));
    }

    private Corrected corrected(WorkNode node, JobId chosen) {
        TrackId leaving = node.track().orElse(null);

        node.subjectSetTo(chosen, SubjectSource.CORRECTED);
        Optional<Track> landed = tracks.place(node, chosen);
        landed.ifPresent(thread -> node.placedIn(thread.id()));
        graph.save(node);

        if (leaving != null && graph.nodesOf(leaving).isEmpty()) {
            graph.deleteTrack(leaving);
        }

        return new Corrected(
                node.id(),
                chosen,
                landed.map(Track::id),
                landed.map(Track::isWeaklyKeyed).orElse(false));
    }
}
