package com.flowops.discovery.infrastructure.process;

import com.flowops.discovery.application.crossing.port.ProcessCompositionPort;
import com.flowops.process.application.published.ProcessAuthoringUseCase;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ThreadCompositionAdapter implements ProcessCompositionPort {
    private final ProcessAuthoringUseCase authoring;

    public ThreadCompositionAdapter(ProcessAuthoringUseCase authoring) {
        this.authoring = authoring;
    }

    @Override
    public UUID composeProcessFrom(String name, List<UUID> taskTemplateIds) {
        return authoring.authorTemplate(
                name,
                null,
                taskTemplateIds.stream()
                        .map(work -> new ProcessAuthoringUseCase.StepSpecification(work, null))
                        .toList());
    }
}
