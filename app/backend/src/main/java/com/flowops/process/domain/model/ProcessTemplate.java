package com.flowops.process.domain.model;

import com.flowops.process.domain.exception.TemplateIsRetiredException;
import com.flowops.process.domain.exception.TemplateNameRequiredException;
import com.flowops.process.domain.exception.TemplateNeedsAStepException;
import com.flowops.process.domain.exception.UnknownStepException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ProcessTemplate {
    private final TemplateId id;
    private final String name;
    private final String overview;
    private final PersonId author;
    private final boolean active;
    private final Instant createdAt;
    private final List<StepDefinition> steps;
    private final List<StepDependency> dependencies;

    private final ProcessMetadata metadata;

    private ProcessTemplate(
            TemplateId id,
            String name,
            String overview,
            PersonId author,
            boolean active,
            Instant createdAt,
            List<StepDefinition> steps,
            List<StepDependency> dependencies,
            ProcessMetadata metadata) {
        this.id = id;
        this.name = name;
        this.overview = overview;
        this.author = author;
        this.active = active;
        this.createdAt = createdAt;
        this.steps = List.copyOf(steps);
        this.dependencies = List.copyOf(dependencies);
        this.metadata = metadata == null ? ProcessMetadata.empty() : metadata;
    }

    public static ProcessTemplate authored(
            TemplateId id, String name, String overview, List<StepDraft> steps, PersonId author, Instant now) {
        if (name == null || name.isBlank()) {
            throw new TemplateNameRequiredException();
        }
        if (steps == null || steps.isEmpty()) {
            throw new TemplateNeedsAStepException();
        }
        return new ProcessTemplate(
                id, name.trim(), overview, author, true, now, defined(steps), List.of(), ProcessMetadata.empty());
    }

    public static ProcessTemplate existing(
            TemplateId id,
            String name,
            String overview,
            PersonId author,
            boolean active,
            Instant createdAt,
            List<StepDefinition> steps,
            List<StepDependency> edges,
            ProcessMetadata metadata) {
        return new ProcessTemplate(id, name, overview, author, active, createdAt, steps, edges, metadata);
    }

    public ProcessTemplate describedBy(ProcessMetadata described) {
        return new ProcessTemplate(id, name, overview, author, active, createdAt, steps, dependencies, described);
    }

    public ProcessMetadata metadata() {
        return metadata;
    }

    public ProcessTemplate editedTo(String overview, List<StepDraft> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new TemplateNeedsAStepException();
        }
        Set<StepId> mine = new LinkedHashSet<>();
        for (StepDefinition step : this.steps) {
            mine.add(step.id());
        }
        for (StepDraft draft : steps) {
            if (draft.id() != null && !mine.contains(draft.id())) {
                throw new UnknownStepException();
            }
        }

        List<StepDefinition> edited = defined(steps);
        Set<StepId> surviving = new LinkedHashSet<>();
        for (StepDefinition step : edited) {
            surviving.add(step.id());
        }
        List<StepDependency> kept = new ArrayList<>();
        for (StepDependency edge : dependencies) {
            if (surviving.contains(edge.dependent()) && surviving.contains(edge.dependsOn())) {
                kept.add(edge);
            }
        }

        DependencyGraph.of(surviving, kept).requireValid();
        return new ProcessTemplate(id, name, overview, author, active, createdAt, edited, kept, metadata);
    }

    public ProcessTemplate retired() {
        if (!active) {
            throw new TemplateIsRetiredException();
        }
        return new ProcessTemplate(id, name, overview, author, false, createdAt, steps, dependencies, metadata);
    }

    public ProcessTemplate dependingOn(StepId dependent, StepId dependsOn) {
        StepDependency edge = StepDependency.of(dependent, dependsOn);
        if (dependencies.contains(edge)) {
            return this;
        }
        graph().with(edge);
        List<StepDependency> widened = new ArrayList<>(dependencies);
        widened.add(edge);
        return new ProcessTemplate(id, name, overview, author, active, createdAt, steps, widened, metadata);
    }

    public ProcessTemplate withoutDependency(StepId dependent, StepId dependsOn) {
        StepDependency edge = StepDependency.of(dependent, dependsOn);
        graph().without(edge);
        List<StepDependency> narrowed = new ArrayList<>(dependencies);
        narrowed.remove(edge);
        return new ProcessTemplate(id, name, overview, author, active, createdAt, steps, narrowed, metadata);
    }

    public boolean alreadyDependsOn(StepId dependent, StepId dependsOn) {
        return dependencies.contains(new StepDependency(dependent, dependsOn));
    }

    public DependencyGraph graph() {
        Set<StepId> ids = new LinkedHashSet<>();
        for (StepDefinition step : steps) {
            ids.add(step.id());
        }
        return DependencyGraph.of(ids, dependencies);
    }

    public TemplateId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String overview() {
        return overview;
    }

    public PersonId author() {
        return author;
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<StepDefinition> steps() {
        return steps;
    }

    public List<StepDependency> dependencies() {
        return dependencies;
    }

    private static List<StepDefinition> defined(List<StepDraft> drafts) {
        List<StepDefinition> defined = new ArrayList<>();
        for (int position = 0; position < drafts.size(); position++) {
            StepDraft draft = drafts.get(position);
            StepId id = draft.id() == null ? StepId.of(UUID.randomUUID()) : draft.id();
            defined.add(StepDefinition.of(
                    id, draft.taskTemplateId(), draft.expectedDurationHours(), position, draft.applicability()));
        }
        return defined;
    }

    public record StepDraft(
            StepId id, TaskTemplateRef taskTemplateId, Integer expectedDurationHours, Applicability applicability) {
        public StepDraft {
            applicability = applicability == null ? Applicability.always() : applicability;
        }

        public static StepDraft added(TaskTemplateRef taskTemplateId, Integer expectedDurationHours) {
            return new StepDraft(null, taskTemplateId, expectedDurationHours, Applicability.always());
        }
    }
}
