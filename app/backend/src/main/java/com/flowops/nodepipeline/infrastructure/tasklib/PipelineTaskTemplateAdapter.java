package com.flowops.nodepipeline.infrastructure.tasklib;

import com.flowops.nodepipeline.application.port.TaskTemplateDraftPort;
import com.flowops.tasklib.application.published.TemplateLifecycleUseCase;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PipelineTaskTemplateAdapter implements TaskTemplateDraftPort {
    private final TemplateLifecycleUseCase templates;

    public PipelineTaskTemplateAdapter(TemplateLifecycleUseCase templates) {
        this.templates = templates;
    }

    @Override
    public Draft draftFor(com.flowops.nodepipeline.domain.CandidateTemplate observed, UUID author) {
        TemplateLifecycleUseCase.DraftedTemplate drafted = templates.createDraft(
                observed.title(),
                observed.description(),
                observed.checklist(),
                observed.responsibleRole(),
                observed.outputKind(),
                author);

        templates.describeDiscovered(
                drafted.id(),
                new com.flowops.tasklib.application.port.TaskTemplatePort.DiscoveredFacts(
                        observed.workType(),
                        observed.keywords(),
                        observed.description(),
                        observed.checklist(),
                        observed.requiredInput(),
                        observed.completionCriteria()));
        return new Draft(drafted.id(), drafted.created());
    }
}
