package com.flowops.process.infrastructure.persistence;

import com.flowops.process.application.shared.port.LoadTemplatePort;
import com.flowops.process.application.shared.port.SaveTemplatePort;
import com.flowops.process.application.shared.port.WorkspacePort;
import com.flowops.process.domain.model.Applicability;
import com.flowops.process.domain.model.DependencyKind;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessMetadata;
import com.flowops.process.domain.model.ProcessTemplate;
import com.flowops.process.domain.model.StepDefinition;
import com.flowops.process.domain.model.StepDependency;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TaskTemplateRef;
import com.flowops.process.domain.model.TemplateId;
import com.flowops.process.infrastructure.persistence.entity.ProcessTemplateJpaEntity;
import com.flowops.process.infrastructure.persistence.entity.StepDefinitionJpaEntity;
import com.flowops.process.infrastructure.persistence.entity.StepDependencyJpaEntity;
import com.flowops.process.infrastructure.persistence.repository.ProcessTemplateJpaRepository;
import com.flowops.process.infrastructure.persistence.repository.StepDefinitionJpaRepository;
import com.flowops.process.infrastructure.persistence.repository.StepDependencyJpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProcessTemplatePersistenceAdapter implements SaveTemplatePort, LoadTemplatePort {
    private final ProcessTemplateJpaRepository templates;
    private final StepDefinitionJpaRepository steps;
    private final StepDependencyJpaRepository edges;
    private final WorkspacePort workspacePort;

    public ProcessTemplatePersistenceAdapter(
            ProcessTemplateJpaRepository templates,
            StepDefinitionJpaRepository steps,
            StepDependencyJpaRepository edges,
            WorkspacePort workspacePort) {
        this.templates = templates;
        this.steps = steps;
        this.edges = edges;
        this.workspacePort = workspacePort;
    }

    @Override
    public void create(ProcessTemplate template) {
        templates.save(new ProcessTemplateJpaEntity(
                template.id().value(),
                workspacePort.currentWorkspaceId(),
                template.name(),
                template.overview(),
                template.author().value(),
                template.active(),
                template.createdAt()));
        for (StepDefinition step : template.steps()) {
            steps.save(rowFor(template.id(), step));
        }
    }

    @Override
    public void replace(ProcessTemplate template) {
        ProcessTemplateJpaEntity stored =
                templates.findById(template.id().value()).orElseThrow();
        stored.setOverview(template.overview());

        stored.describedBy(
                template.metadata().triggerNote(),
                template.metadata().endCondition(),
                template.metadata().ownerRole());
        templates.save(stored);

        Set<UUID> surviving = new HashSet<>();
        for (StepDefinition step : template.steps()) {
            surviving.add(step.id().value());
            steps.save(rowFor(template.id(), step));
        }
        for (StepDependencyJpaEntity edge : edges.findByTemplateId(template.id().value())) {
            if (!surviving.contains(edge.getDependentStepId()) || !surviving.contains(edge.getDependsOnStepId())) {
                edges.delete(edge);
            }
        }
        for (StepDefinitionJpaEntity step :
                steps.findByTemplateIdOrderByPosition(template.id().value())) {
            if (!surviving.contains(step.getId())) {
                steps.delete(step);
            }
        }
    }

    @Override
    public void addDependency(
            TemplateId template, StepDependency edge, DependencyKind kind, BigDecimal confidence, Instant at) {
        edges.save(new StepDependencyJpaEntity(
                template.value(), edge.dependent().value(), edge.dependsOn().value(), kind.name(), confidence, at));
    }

    @Override
    public boolean promoteDependency(TemplateId template, StepDependency edge, PersonId by, Instant at) {
        return edges.findById(new StepDependencyJpaEntity.Key(
                        template.value(),
                        edge.dependent().value(),
                        edge.dependsOn().value()))
                .map(found -> {
                    found.promotedBy(by.value(), at);
                    edges.save(found);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public void removeDependency(TemplateId template, StepDependency edge) {
        edges.deleteById(new StepDependencyJpaEntity.Key(
                template.value(), edge.dependent().value(), edge.dependsOn().value()));
    }

    @Override
    public Optional<ProcessTemplate> findById(TemplateId id) {
        return templates.findById(id.value()).map(this::rehydrate);
    }

    @Override
    public void retire(ProcessTemplate template) {
        ProcessTemplateJpaEntity stored =
                templates.findById(template.id().value()).orElseThrow();
        stored.retire();
        templates.save(stored);
    }

    @Override
    public void markComposedDraft(TemplateId template) {
        ProcessTemplateJpaEntity stored = templates.findById(template.value()).orElseThrow();
        stored.composedFromDiscovery();
        templates.save(stored);
    }

    @Override
    public List<ProcessTemplate> findAllActive() {
        List<ProcessTemplate> all = new ArrayList<>();
        for (ProcessTemplateJpaEntity stored : templates.findByActiveTrueOrderByCreatedAtDesc()) {
            all.add(rehydrate(stored));
        }
        return all;
    }

    @Override
    public boolean activeNameExists(String name) {
        return templates.existsActiveByName(name);
    }

    @Override
    public Optional<TemplateId> templateOf(StepId step) {
        return steps.findById(step.value()).map(found -> TemplateId.of(found.getTemplateId()));
    }

    private ProcessTemplate rehydrate(ProcessTemplateJpaEntity stored) {
        List<StepDefinition> defined = new ArrayList<>();
        for (StepDefinitionJpaEntity step : steps.findByTemplateIdOrderByPosition(stored.getId())) {
            defined.add(new StepDefinition(
                    StepId.of(step.getId()),
                    TaskTemplateRef.of(step.getTaskTemplateId()),
                    step.getExpectedDurationHours(),
                    step.getPosition(),
                    new Applicability(step.isOptional(), step.getConditionNote())));
        }

        List<StepDependency> drawn = new ArrayList<>();
        for (StepDependencyJpaEntity edge :
                edges.findByTemplateIdAndKind(stored.getId(), DependencyKind.CONFIRMED.name())) {
            drawn.add(new StepDependency(StepId.of(edge.getDependentStepId()), StepId.of(edge.getDependsOnStepId())));
        }
        return ProcessTemplate.existing(
                TemplateId.of(stored.getId()),
                stored.getName(),
                stored.getOverview(),
                PersonId.of(stored.getAuthorUserId()),
                stored.isActive(),
                stored.getCreatedAt(),
                defined,
                drawn,
                new ProcessMetadata(stored.getTriggerNote(), stored.getEndCondition(), stored.getOwnerRole()));
    }

    private StepDefinitionJpaEntity rowFor(TemplateId template, StepDefinition step) {
        return new StepDefinitionJpaEntity(
                step.id().value(),
                template.value(),
                step.taskTemplateId().value(),
                step.expectedDurationHours(),
                step.position(),
                step.applicability().optional(),
                step.applicability().conditionNote());
    }
}
