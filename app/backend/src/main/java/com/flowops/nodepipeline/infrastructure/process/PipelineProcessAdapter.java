package com.flowops.nodepipeline.infrastructure.process;

import com.flowops.nodepipeline.application.port.ProcessRunPort;
import com.flowops.process.application.published.ProcessInstantiationUseCase;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PipelineProcessAdapter implements ProcessRunPort {
    private final ProcessInstantiationUseCase instantiation;

    public PipelineProcessAdapter(ProcessInstantiationUseCase instantiation) {
        this.instantiation = instantiation;
    }

    @Override
    public List<StartableProcess> startable() {
        return instantiation.startableTemplates().stream()
                .map(template ->
                        new StartableProcess(template.id(), template.name(), template.overview(), template.stepCount()))
                .toList();
    }

    @Override
    public UUID startRun(UUID templateId, String name, UUID processOwner) {
        return instantiation.startRun(templateId, name, processOwner);
    }
}
