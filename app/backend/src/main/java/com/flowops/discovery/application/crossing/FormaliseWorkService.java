package com.flowops.discovery.application.crossing;

import com.flowops.discovery.application.crossing.port.TemplateCreationPort;
import com.flowops.discovery.application.shared.exception.NodeAlreadyTemplatedException;
import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.exception.UnknownWorkNodeException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.model.WorkNode;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormaliseWorkService implements FormaliseWorkUseCase {
    private final IdentifyCallerPort caller;
    private final WorkGraphPort graph;
    private final TemplateCreationPort templates;

    public FormaliseWorkService(IdentifyCallerPort caller, WorkGraphPort graph, TemplateCreationPort templates) {
        this.caller = caller;
        this.graph = graph;
        this.templates = templates;
    }

    @Override
    @Transactional
    public Formalised execute(Formalise command) {
        UUID me = caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        WorkNodeId id = WorkNodeId.of(command.nodeId());
        WorkNode node =
                graph.findNode(id).orElseThrow(() -> new UnknownWorkNodeException("no unit of work " + id.value()));

        if (node.taskTemplateId().isPresent()) {
            throw new NodeAlreadyTemplatedException("unit of work " + id.value() + " is already a task template");
        }

        UUID template = templates.createTemplateFor(command.title(), command.detail(), command.steps(), me);

        node.formalisedAs(template);
        graph.save(node);

        return new Formalised(command.nodeId(), template);
    }
}
