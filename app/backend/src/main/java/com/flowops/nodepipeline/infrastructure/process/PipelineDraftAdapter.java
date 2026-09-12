package com.flowops.nodepipeline.infrastructure.process;

import com.flowops.nodepipeline.application.port.ProcessDraftPort;
import com.flowops.nodepipeline.domain.compose.DraftProcess;
import com.flowops.process.application.published.ProcessAuthoringUseCase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PipelineDraftAdapter implements ProcessDraftPort {
    private final ProcessAuthoringUseCase authoring;

    public PipelineDraftAdapter(ProcessAuthoringUseCase authoring) {
        this.authoring = authoring;
    }

    @Override
    @Transactional
    public UUID save(DraftProcess draft) {
        List<ProcessAuthoringUseCase.StepSpecification> steps = new ArrayList<>();
        Map<String, String> titleOfStep = new HashMap<>();

        for (DraftProcess.DraftStep step : draft.steps()) {
            titleOfStep.put(step.id(), step.templateTitle());
            steps.add(new ProcessAuthoringUseCase.StepSpecification(libraryEntry(step), null));
        }

        UUID template = authoring.authorComposedDraft(draft.name(), null, steps);

        for (DraftProcess.DraftEdge edge : draft.observedEdges()) {
            String dependent = titleOfStep.get(edge.dependentStepId());
            String dependsOn = titleOfStep.get(edge.dependsOnStepId());

            if (dependent != null && dependsOn != null) {
                authoring.addDependency(template, dependent, dependsOn, "OBSERVED", edge.confidence());
            }
        }

        return template;
    }

    private static UUID libraryEntry(DraftProcess.DraftStep step) {
        try {
            return UUID.fromString(step.taskTemplateId());
        } catch (IllegalArgumentException | NullPointerException notAnIdentifier) {
            throw new UnresolvedDraftStepException("step " + step.id() + " has no task template behind it: "
                    + step.taskTemplateId() + ". Resolve or mint it before composing.");
        }
    }
}
