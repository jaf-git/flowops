package com.flowops.discovery.application.crossing;

import com.flowops.discovery.application.crossing.port.ProcessCompositionPort;
import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.exception.TrackNotFullyTemplatedException;
import com.flowops.discovery.application.shared.exception.UnknownTrackException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.model.Track;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.WorkNode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComposeProcessService implements ComposeProcessUseCase {
    private final IdentifyCallerPort caller;
    private final WorkGraphPort graph;
    private final ProcessCompositionPort processes;

    public ComposeProcessService(IdentifyCallerPort caller, WorkGraphPort graph, ProcessCompositionPort processes) {
        this.caller = caller;
        this.graph = graph;
        this.processes = processes;
    }

    @Override
    @Transactional
    public Composed execute(Compose command) {
        caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        TrackId id = TrackId.of(command.trackId());
        Track thread =
                graph.findTrack(id).orElseThrow(() -> new UnknownTrackException("no thread of work " + id.value()));

        List<WorkNode> units = graph.nodesOf(id);
        if (units.isEmpty()
                || units.stream().anyMatch(unit -> unit.taskTemplateId().isEmpty())) {
            throw new TrackNotFullyTemplatedException(
                    "thread of work " + id.value() + " holds work that is not yet a task template");
        }

        UUID composed = processes.composeProcessFrom(
                command.name(),
                units.stream().map(unit -> unit.taskTemplateId().orElseThrow()).toList());

        thread.composedAs(composed);
        graph.save(thread);

        return new Composed(command.trackId(), composed);
    }
}
