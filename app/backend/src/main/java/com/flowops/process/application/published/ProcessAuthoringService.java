package com.flowops.process.application.published;

import static com.flowops.process.application.published.ProcessRefusals.refusingAsPublished;

import com.flowops.process.application.authortemplate.AuthorTemplateCommand;
import com.flowops.process.application.authortemplate.AuthorTemplateUseCase;
import com.flowops.process.application.definedependency.DefineDependencyCommand;
import com.flowops.process.application.definedependency.DefineDependencyUseCase;
import com.flowops.process.application.definedependency.RemoveDependencyUseCase;
import com.flowops.process.application.edittemplate.EditTemplateCommand;
import com.flowops.process.application.edittemplate.EditTemplateUseCase;
import com.flowops.process.application.shared.port.SaveTemplatePort;
import com.flowops.process.application.shared.port.TaskTemplateContentPort;
import com.flowops.process.application.viewtemplates.ViewTemplatesUseCase;
import com.flowops.process.domain.model.DependencyKind;
import com.flowops.process.domain.model.ProcessTemplate;
import com.flowops.process.domain.model.StepDefinition;
import com.flowops.process.domain.model.StepDependency;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TaskTemplateRef;
import com.flowops.process.domain.model.TaskTemplateWork;
import com.flowops.process.domain.model.TemplateId;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessAuthoringService implements ProcessAuthoringUseCase {
    private final AuthorTemplateUseCase authorTemplate;
    private final EditTemplateUseCase editTemplate;
    private final DefineDependencyUseCase defineDependency;
    private final RemoveDependencyUseCase removeDependency;
    private final ViewTemplatesUseCase viewTemplates;
    private final TaskTemplateContentPort taskTemplateContentPort;
    private final SaveTemplatePort saveTemplatePort;
    private final Clock clock;

    public ProcessAuthoringService(
            AuthorTemplateUseCase authorTemplate,
            EditTemplateUseCase editTemplate,
            DefineDependencyUseCase defineDependency,
            RemoveDependencyUseCase removeDependency,
            ViewTemplatesUseCase viewTemplates,
            TaskTemplateContentPort taskTemplateContentPort,
            SaveTemplatePort saveTemplatePort,
            Clock clock) {
        this.authorTemplate = authorTemplate;
        this.editTemplate = editTemplate;
        this.defineDependency = defineDependency;
        this.removeDependency = removeDependency;
        this.viewTemplates = viewTemplates;
        this.taskTemplateContentPort = taskTemplateContentPort;
        this.saveTemplatePort = saveTemplatePort;
        this.clock = clock;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PROCESS_TEMPLATE_AUTHOR')")
    public UUID authorTemplate(String name, String overview, List<StepSpecification> steps) {
        UUID[] authored = new UUID[1];
        refusingAsPublished(() -> authored[0] = authorTemplate
                .execute(new AuthorTemplateCommand(name, overview, drafted(steps)))
                .id()
                .value());
        return authored[0];
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PROCESS_TEMPLATE_AUTHOR')")
    public UUID authorComposedDraft(String name, String overview, List<StepSpecification> steps) {
        UUID[] composed = new UUID[1];
        refusingAsPublished(() -> {
            TemplateId id = authorTemplate
                    .execute(new AuthorTemplateCommand(name, overview, drafted(steps)))
                    .id();
            saveTemplatePort.markComposedDraft(id);
            composed[0] = id.value();
        });
        return composed[0];
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PROCESS_TEMPLATE_EDIT')")
    public void appendSteps(UUID templateId, List<StepSpecification> steps) {
        refusingAsPublished(() -> {
            TemplateId id = TemplateId.of(templateId);
            ProcessTemplate existing = viewTemplates.one(id);

            List<ProcessTemplate.StepDraft> all = new ArrayList<>(keeping(existing));
            all.addAll(drafted(steps));

            editTemplate.execute(new EditTemplateCommand(id, existing.overview(), all));
        });
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PROCESS_TEMPLATE_EDIT')")
    public void insertStep(UUID templateId, int position, StepSpecification step) {
        refusingAsPublished(() -> {
            TemplateId id = TemplateId.of(templateId);
            ProcessTemplate existing = viewTemplates.one(id);

            boolean alreadyPlanned = existing.steps().stream()
                    .anyMatch(each -> each.taskTemplateId().value().equals(step.taskTemplateId()));
            if (alreadyPlanned) {
                return;
            }

            List<ProcessTemplate.StepDraft> all = new ArrayList<>(keeping(existing));
            all.add(
                    Math.min(Math.max(position, 0), all.size()),
                    drafted(List.of(step)).getFirst());

            editTemplate.execute(new EditTemplateCommand(id, existing.overview(), all));
        });
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PROCESS_TEMPLATE_AUTHOR')")
    public void addDependency(
            UUID templateId, String dependentTitle, String dependsOnTitle, String kind, Double confidence) {
        refusingAsPublished(() -> {
            TemplateId id = TemplateId.of(templateId);
            ProcessTemplate existing = viewTemplates.one(id);

            Optional<StepId> dependent = stepTitled(existing, dependentTitle);
            Optional<StepId> dependsOn = stepTitled(existing, dependsOnTitle);
            if (dependent.isEmpty() || dependsOn.isEmpty() || dependent.equals(dependsOn)) {
                return;
            }

            if (DependencyKind.CONFIRMED.name().equals(kind)) {
                defineDependency.execute(new DefineDependencyCommand(id, dependent.get(), dependsOn.get()));
                return;
            }

            StepDependency edge = new StepDependency(dependent.get(), dependsOn.get());
            if (existing.alreadyDependsOn(dependent.get(), dependsOn.get())) {
                return;
            }

            saveTemplatePort.addDependency(
                    id,
                    edge,
                    DependencyKind.OBSERVED,
                    confidence == null ? null : BigDecimal.valueOf(confidence),
                    clock.instant());
        });
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('PROCESS_TEMPLATE_AUTHOR')")
    public void removeDependency(UUID templateId, String dependentTitle, String dependsOnTitle) {
        refusingAsPublished(() -> {
            TemplateId id = TemplateId.of(templateId);
            ProcessTemplate existing = viewTemplates.one(id);

            Optional<StepId> dependent = stepTitled(existing, dependentTitle);
            Optional<StepId> dependsOn = stepTitled(existing, dependsOnTitle);
            if (dependent.isEmpty() || dependsOn.isEmpty()) {
                return;
            }

            removeDependency.execute(new DefineDependencyCommand(id, dependent.get(), dependsOn.get()));
        });
    }

    private List<ProcessTemplate.StepDraft> keeping(ProcessTemplate existing) {
        return existing.steps().stream()
                .map(step -> new ProcessTemplate.StepDraft(
                        step.id(), step.taskTemplateId(), step.expectedDurationHours(), step.applicability()))
                .toList();
    }

    private Optional<StepId> stepTitled(ProcessTemplate template, String title) {
        String wanted = normalised(title);
        Map<TaskTemplateRef, TaskTemplateWork> work = taskTemplateContentPort.contentOf(
                template.steps().stream().map(StepDefinition::taskTemplateId).toList());
        return template.steps().stream()
                .filter(step -> {
                    TaskTemplateWork words = work.get(step.taskTemplateId());
                    return words != null && normalised(words.title()).equals(wanted);
                })
                .map(StepDefinition::id)
                .findFirst();
    }

    private static String normalised(String title) {
        return title == null
                ? ""
                : title.toLowerCase(Locale.ROOT)
                        .replaceAll("[^\\p{L}\\p{N}]+", " ")
                        .trim();
    }

    private List<ProcessTemplate.StepDraft> drafted(List<StepSpecification> steps) {
        return steps.stream()
                .map(step -> ProcessTemplate.StepDraft.added(
                        TaskTemplateRef.of(step.taskTemplateId()), step.expectedDurationHours()))
                .toList();
    }
}
