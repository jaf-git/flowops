package com.flowops.aiinsight.infrastructure.process;

import com.flowops.aiinsight.application.port.ApplyProcessChangePort;
import com.flowops.aiinsight.application.port.IdentifyCallerPort;
import com.flowops.process.application.published.ProcessAuthoringUseCase;
import com.flowops.process.application.published.ProcessAuthoringUseCase.StepSpecification;
import com.flowops.tasklib.application.published.TemplateResolutionUseCase;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProcessChangeAdapter implements ApplyProcessChangePort {
    private final ProcessAuthoringUseCase processes;
    private final TemplateResolutionUseCase templates;
    private final IdentifyCallerPort caller;

    public ProcessChangeAdapter(
            ProcessAuthoringUseCase processes, TemplateResolutionUseCase templates, IdentifyCallerPort caller) {
        this.processes = processes;
        this.templates = templates;
        this.caller = caller;
    }

    @Override
    public void insertStep(UUID templateId, int position, String title) {
        UUID author = caller.currentCaller()
                .orElseThrow(() -> new IllegalStateException(
                        "applying a finding requires a caller, and UC-09 admits no unattended path"));
        UUID work = templates.resolve(title, null, author);
        processes.insertStep(templateId, position, new StepSpecification(work, null));
    }

    @Override
    public void removeDependency(UUID templateId, String dependentTitle, String dependsOnTitle) {
        processes.removeDependency(templateId, dependentTitle, dependsOnTitle);
    }
}
