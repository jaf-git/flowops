package com.flowops.process.application.published;

import static com.flowops.process.application.published.ProcessRefusals.refusingAsPublished;

import com.flowops.process.application.instantiate.InstantiateCommand;
import com.flowops.process.application.instantiate.InstantiateUseCase;
import com.flowops.process.application.startfromtasks.StartFromDescriptionsCommand;
import com.flowops.process.application.startfromtasks.StartFromDescriptionsUseCase;
import com.flowops.process.application.viewtemplates.ViewTemplatesUseCase;
import com.flowops.process.domain.model.InstanceStep;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessInstance;
import com.flowops.process.domain.model.TemplateId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessInstantiationService implements ProcessInstantiationUseCase {
    private final ViewTemplatesUseCase viewTemplates;
    private final InstantiateUseCase instantiate;
    private final StartFromDescriptionsUseCase startFromDescriptions;

    public ProcessInstantiationService(
            ViewTemplatesUseCase viewTemplates,
            InstantiateUseCase instantiate,
            StartFromDescriptionsUseCase startFromDescriptions) {
        this.viewTemplates = viewTemplates;
        this.instantiate = instantiate;
        this.startFromDescriptions = startFromDescriptions;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PROCESS_INSTANTIATE')")
    public StartedRun startRunFromDescriptions(String name, UUID processOwnerId, List<NewStep> steps) {
        StartedRun[] started = new StartedRun[1];
        refusingAsPublished(() -> started[0] = asStarted(startFromDescriptions.execute(new StartFromDescriptionsCommand(
                name,
                PersonId.of(processOwnerId),
                steps.stream()
                        .map(step -> new StartFromDescriptionsCommand.NewStep(
                                step.title(),
                                step.description(),
                                PersonId.of(step.assignee()),
                                step.deadline(),
                                step.priority()))
                        .toList()))));
        return started[0];
    }

    private static StartedRun asStarted(ProcessInstance instance) {
        return new StartedRun(
                instance.id().value(),
                instance.steps().stream()
                        .sorted(java.util.Comparator.comparingInt(InstanceStep::position))
                        .map(step -> Objects.requireNonNull(step.task(), "a described step is created with its task")
                                .value())
                        .toList());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('PROCESS_INSTANTIATE')")
    public List<StartableTemplate> startableTemplates() {
        return viewTemplates.all().stream()
                .map(template -> new StartableTemplate(
                        template.id().value(),
                        template.name(),
                        template.overview(),
                        template.steps().size()))
                .toList();
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PROCESS_INSTANTIATE')")
    public UUID startRun(UUID templateId, String name, UUID processOwner) {
        TemplateId template = TemplateId.of(templateId);
        UUID[] started = new UUID[1];
        refusingAsPublished(() -> {
            String called =
                    name == null || name.isBlank() ? viewTemplates.one(template).name() : name.strip();
            started[0] = instantiate
                    .execute(new InstantiateCommand(template, called, PersonId.of(processOwner)))
                    .id()
                    .value();
        });
        return started[0];
    }
}
