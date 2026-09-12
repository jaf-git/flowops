package com.flowops.process.domain.model;

import com.flowops.process.domain.enums.InstanceState;
import com.flowops.process.domain.enums.StepCondition;
import com.flowops.process.domain.exception.AbandonReasonRequiredException;
import com.flowops.process.domain.exception.ClosureNoteRequiredException;
import com.flowops.process.domain.exception.IllegalStepTransitionException;
import com.flowops.process.domain.exception.InstanceNeedsATaskException;
import com.flowops.process.domain.exception.InstanceNotRunningException;
import com.flowops.process.domain.exception.StepDoesNotApplyOptionallyException;
import com.flowops.process.domain.exception.StepNotAwaitingADecisionException;
import com.flowops.process.domain.exception.StepNotReachableException;
import com.flowops.process.domain.exception.UnknownStepException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ProcessInstance {
    private final InstanceId id;
    private final String name;
    private final TemplateId template;
    private final PersonId owner;
    private final PersonId startedBy;
    private final InstanceState state;
    private final Instant startedAt;
    private final Instant completedAt;
    private final List<InstanceStep> steps;
    private final List<StepDependency> dependencies;
    private final Abandonment abandonment;

    private final String closureNote;

    public record Abandonment(Instant at, String reason) {}

    private ProcessInstance(
            InstanceId id,
            String name,
            TemplateId template,
            PersonId owner,
            PersonId startedBy,
            InstanceState state,
            Instant startedAt,
            Instant completedAt,
            List<InstanceStep> steps,
            List<StepDependency> dependencies) {
        this(id, name, template, owner, startedBy, state, startedAt, completedAt, steps, dependencies, null, null);
    }

    private ProcessInstance(
            InstanceId id,
            String name,
            TemplateId template,
            PersonId owner,
            PersonId startedBy,
            InstanceState state,
            Instant startedAt,
            Instant completedAt,
            List<InstanceStep> steps,
            List<StepDependency> dependencies,
            Abandonment abandonment) {
        this(
                id,
                name,
                template,
                owner,
                startedBy,
                state,
                startedAt,
                completedAt,
                steps,
                dependencies,
                abandonment,
                null);
    }

    private ProcessInstance(
            InstanceId id,
            String name,
            TemplateId template,
            PersonId owner,
            PersonId startedBy,
            InstanceState state,
            Instant startedAt,
            Instant completedAt,
            List<InstanceStep> steps,
            List<StepDependency> dependencies,
            Abandonment abandonment,
            String closureNote) {
        this.abandonment = abandonment;
        this.closureNote = closureNote;
        this.id = id;
        this.name = name;
        this.template = template;
        this.owner = owner;
        this.startedBy = startedBy;
        this.state = state;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.steps = List.copyOf(steps);
        this.dependencies = List.copyOf(dependencies);
    }

    public static ProcessInstance cutFrom(
            InstanceId id,
            ProcessTemplate template,
            Map<TaskTemplateRef, TaskTemplateWork> work,
            String name,
            PersonId owner,
            PersonId startedBy,
            Instant now) {
        template.graph().requireValid();

        Map<StepId, StepId> copied = new HashMap<>();
        List<InstanceStep> steps = new ArrayList<>();
        for (StepDefinition definition : template.steps()) {
            StepId copy = StepId.of(UUID.randomUUID());
            copied.put(definition.id(), copy);
            TaskTemplateWork words = work.get(definition.taskTemplateId());
            if (words == null) {
                throw new IllegalArgumentException(
                        "no task template content supplied for step " + (definition.position() + 1));
            }
            steps.add(InstanceStep.pending(copy, definition, words));
        }

        List<StepDependency> edges = new ArrayList<>();
        for (StepDependency edge : template.dependencies()) {
            edges.add(new StepDependency(copied.get(edge.dependent()), copied.get(edge.dependsOn())));
        }

        Set<StepId> entries = DependencyGraph.of(copied.values(), edges).entrySteps();
        List<InstanceStep> evaluated = new ArrayList<>();
        for (InstanceStep step : steps) {
            evaluated.add(entries.contains(step.id()) ? step.reachableAt(now) : step);
        }

        return new ProcessInstance(
                id, name, template.id(), owner, startedBy, InstanceState.RUNNING, now, null, evaluated, edges);
    }

    public static ProcessInstance startedFromTasks(
            InstanceId id, String name, PersonId owner, PersonId startedBy, List<InstanceStep> steps, Instant now) {
        if (steps.isEmpty()) {
            throw new InstanceNeedsATaskException();
        }
        return new ProcessInstance(id, name, null, owner, startedBy, InstanceState.RUNNING, now, null, steps, List.of())
                .withNewlyReachable(now);
    }

    public static ProcessInstance existing(
            InstanceId id,
            String name,
            TemplateId template,
            PersonId owner,
            PersonId startedBy,
            InstanceState state,
            Instant startedAt,
            Instant completedAt,
            List<InstanceStep> steps,
            List<StepDependency> edges,
            Abandonment abandonment,
            String closureNote) {
        return new ProcessInstance(
                id,
                name,
                template,
                owner,
                startedBy,
                state,
                startedAt,
                completedAt,
                steps,
                edges,
                abandonment,
                closureNote);
    }

    public ProcessInstance assigned(StepId step, TaskRef created, PersonId person, Instant now) {
        requireAssignable(step);
        List<InstanceStep> moved = new ArrayList<>();
        for (InstanceStep each : steps) {
            moved.add(each.id().equals(step) ? each.assignedTo(created, person) : each);
        }
        return new ProcessInstance(
                id, name, template, owner, startedBy, state, startedAt, completedAt, moved, dependencies);
    }

    public void requireAssignable(StepId step) {
        InstanceStep found = stepOf(step);
        if (found.condition() == StepCondition.PENDING) {
            throw new StepNotReachableException(unmetDependenciesOf(step));
        }
        if (found.condition() != StepCondition.REACHABLE) {
            throw new IllegalStepTransitionException(found.condition());
        }
    }

    public ProcessInstance closed(StepId step, Instant now) {
        if (stepOf(step).isClosed()) {
            return this;
        }
        List<InstanceStep> afterClosing = new ArrayList<>();
        for (InstanceStep each : steps) {
            afterClosing.add(each.id().equals(step) ? each.closedAt(now) : each);
        }
        ProcessInstance closed = new ProcessInstance(
                id, name, template, owner, startedBy, state, startedAt, completedAt, afterClosing, dependencies);
        return closed.withNewlyReachable(now);
    }

    public ProcessInstance skipped(StepId step, Instant now) {
        InstanceStep target = stepOf(step);
        if (target.isClosed()) {
            return this;
        }
        if (!target.applicability().needsADecision()) {
            throw new StepDoesNotApplyOptionallyException();
        }
        if (!target.isReachable()) {
            throw new StepNotAwaitingADecisionException(step);
        }
        List<InstanceStep> afterSkipping = new ArrayList<>();
        for (InstanceStep each : steps) {
            afterSkipping.add(each.id().equals(step) ? each.skippedAt(now) : each);
        }
        ProcessInstance skipped = new ProcessInstance(
                id, name, template, owner, startedBy, state, startedAt, completedAt, afterSkipping, dependencies);
        return skipped.withNewlyReachable(now);
    }

    public ProcessInstance returnedToReachable(StepId step) {
        if (stepOf(step).isReachable()) {
            return this;
        }
        List<InstanceStep> handedBack = new ArrayList<>();
        for (InstanceStep each : steps) {
            handedBack.add(each.id().equals(step) ? each.returnedToReachable() : each);
        }
        return new ProcessInstance(
                id, name, template, owner, startedBy, state, startedAt, completedAt, handedBack, dependencies);
    }

    public ProcessInstance with(InstanceStep step, List<StepDependency> waitsFor, Instant now) {
        requireRunning();
        List<InstanceStep> widened = new ArrayList<>(steps);
        widened.add(step);
        List<StepDependency> edges = new ArrayList<>(dependencies);
        edges.addAll(waitsFor);

        Set<StepId> ids = new LinkedHashSet<>();
        for (InstanceStep each : widened) {
            ids.add(each.id());
        }
        DependencyGraph.of(ids, edges).requireValid();

        return new ProcessInstance(id, name, template, owner, startedBy, state, startedAt, completedAt, widened, edges)
                .reevaluated(now);
    }

    public ProcessInstance without(StepId step, Instant now) {
        requireRunning();
        stepOf(step);
        if (steps.size() == 1) {
            throw new InstanceNeedsATaskException();
        }
        List<InstanceStep> remaining = new ArrayList<>();

        int position = 0;
        for (InstanceStep each : steps) {
            if (!each.id().equals(step)) {
                remaining.add(each.at(position));
                position++;
            }
        }
        List<StepDependency> surviving = new ArrayList<>();
        for (StepDependency edge : dependencies) {
            if (!edge.dependent().equals(step) && !edge.dependsOn().equals(step)) {
                surviving.add(edge);
            }
        }
        return new ProcessInstance(
                        id, name, template, owner, startedBy, state, startedAt, completedAt, remaining, surviving)
                .reevaluated(now);
    }

    public ProcessInstance reordered(List<StepId> order) {
        requireRunning();
        Set<StepId> asked = new LinkedHashSet<>(order);
        if (asked.size() != order.size() || asked.size() != steps.size()) {
            throw new UnknownStepException();
        }
        Map<StepId, InstanceStep> byId = new HashMap<>();
        for (InstanceStep each : steps) {
            byId.put(each.id(), each);
        }
        List<InstanceStep> moved = new ArrayList<>();
        for (int position = 0; position < order.size(); position++) {
            InstanceStep found = byId.get(order.get(position));
            if (found == null) {
                throw new UnknownStepException();
            }
            moved.add(found.at(position));
        }
        return new ProcessInstance(
                id, name, template, owner, startedBy, state, startedAt, completedAt, moved, dependencies);
    }

    public ProcessInstance withEdge(StepDependency edge, Instant now) {
        requireRunning();
        DependencyGraph widened = graph().with(edge);
        if (widened.edges().size() == dependencies.size()) {
            return this;
        }
        return rewired(widened, now);
    }

    public ProcessInstance withoutEdge(StepDependency edge, Instant now) {
        requireRunning();
        stepOf(edge.dependent());
        stepOf(edge.dependsOn());
        if (!graph().contains(edge)) {
            return this;
        }
        return rewired(graph().without(edge), now);
    }

    private ProcessInstance rewired(DependencyGraph graph, Instant now) {
        return new ProcessInstance(
                        id,
                        name,
                        template,
                        owner,
                        startedBy,
                        state,
                        startedAt,
                        completedAt,
                        steps,
                        new ArrayList<>(graph.edges()))
                .reevaluated(now);
    }

    public ProcessInstance reevaluated(Instant now) {
        List<InstanceStep> settled = new ArrayList<>();
        for (InstanceStep each : steps) {
            settled.add(each.isReachable() && !unmetDependenciesOf(each.id()).isEmpty() ? each.backToPending() : each);
        }
        return new ProcessInstance(
                        id, name, template, owner, startedBy, state, startedAt, completedAt, settled, dependencies)
                .withNewlyReachable(now);
    }

    private void requireRunning() {
        if (state != InstanceState.RUNNING) {
            throw new InstanceNotRunningException();
        }
    }

    public ProcessInstance withNewlyReachable(Instant now) {
        List<InstanceStep> evaluated = new ArrayList<>();

        boolean complete = !steps.isEmpty();
        for (InstanceStep each : steps) {
            InstanceStep next = each;
            if (each.condition() == StepCondition.PENDING
                    && unmetDependenciesOf(each.id()).isEmpty()) {
                next = each.reachableAt(now);
            }
            evaluated.add(next);
            complete = complete && next.isClosed();
        }
        return new ProcessInstance(
                id,
                name,
                template,
                owner,
                startedBy,
                complete ? InstanceState.COMPLETE : state,
                startedAt,
                complete && completedAt == null ? now : completedAt,
                evaluated,
                dependencies);
    }

    public Set<StepId> newlyReachableSince(ProcessInstance before) {
        Set<StepId> opened = new LinkedHashSet<>();
        for (InstanceStep each : steps) {
            if (each.isReachable() && !before.stepOf(each.id()).isReachable()) {
                opened.add(each.id());
            }
        }
        return opened;
    }

    public boolean heldByAnyOf(Set<PersonId> people) {
        for (InstanceStep step : steps) {
            if (step.assignee() != null && people.contains(step.assignee())) {
                return true;
            }
        }
        return false;
    }

    public Set<StepId> unmetDependenciesOf(StepId step) {
        Set<StepId> unmet = new LinkedHashSet<>();
        for (StepDependency edge : dependencies) {
            if (edge.dependent().equals(step) && !stepOf(edge.dependsOn()).isClosed()) {
                unmet.add(edge.dependsOn());
            }
        }
        return unmet;
    }

    public InstanceStep stepOf(StepId step) {
        for (InstanceStep each : steps) {
            if (each.id().equals(step)) {
                return each;
            }
        }
        throw new UnknownStepException();
    }

    public Set<StepId> awaitingAssignment() {
        if (state != InstanceState.RUNNING) {
            return Set.of();
        }
        Set<StepId> waiting = new LinkedHashSet<>();
        for (InstanceStep step : steps) {
            if (step.isReachable()) {
                waiting.add(step.id());
            }
        }
        return waiting;
    }

    public Progress progress() {
        int closed = 0;
        for (InstanceStep step : steps) {
            if (step.isClosed()) {
                closed++;
            }
        }
        return new Progress(closed, steps.size());
    }

    public Optional<Bottleneck> bottleneck(Instant now) {
        return bottleneck(now, Map.of());
    }

    public Optional<Bottleneck> bottleneck(Instant now, Map<StepId, Duration> waitingByStep) {
        Bottleneck worst = null;
        for (InstanceStep step : steps) {
            if (step.isClosed() || step.reachableAt() == null) {
                continue;
            }
            Duration waited = waitingByStep.containsKey(step.id())
                    ? waitingByStep.get(step.id())
                    : Duration.between(step.reachableAt(), now);
            if (worst == null || waited.compareTo(worst.waitedFor()) > 0) {
                worst = new Bottleneck(step.id(), waited);
            }
        }
        return Optional.ofNullable(worst);
    }

    public boolean isComplete() {
        if (steps.isEmpty()) {
            return false;
        }
        for (InstanceStep step : steps) {
            if (!step.isClosed()) {
                return false;
            }
        }
        return true;
    }

    public Optional<Duration> totalDuration() {
        return completedAt == null ? Optional.empty() : Optional.of(Duration.between(startedAt, completedAt));
    }

    public DependencyGraph graph() {
        Set<StepId> ids = new LinkedHashSet<>();
        for (InstanceStep step : steps) {
            ids.add(step.id());
        }
        return DependencyGraph.of(ids, dependencies);
    }

    public InstanceId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public TemplateId template() {
        return template;
    }

    public PersonId owner() {
        return owner;
    }

    public PersonId startedBy() {
        return startedBy;
    }

    public ProcessInstance abandonedWith(String reason, Instant at) {
        requireRunning();

        if (reason == null || reason.isBlank()) {
            throw new AbandonReasonRequiredException();
        }
        return new ProcessInstance(
                id,
                name,
                template,
                owner,
                startedBy,
                InstanceState.ABANDONED,
                startedAt,
                completedAt,
                steps,
                dependencies,
                new Abandonment(at, reason.trim()));
    }

    public ProcessInstance closedEarlyWith(String note, Instant at) {
        requireRunning();

        if (note == null || note.isBlank()) {
            throw new ClosureNoteRequiredException();
        }
        return new ProcessInstance(
                id,
                name,
                template,
                owner,
                startedBy,
                InstanceState.COMPLETE,
                startedAt,
                at,
                steps,
                dependencies,
                abandonment,
                note.trim());
    }

    public Optional<Abandonment> abandonment() {
        return Optional.ofNullable(abandonment);
    }

    public Optional<String> closureNote() {
        return Optional.ofNullable(closureNote);
    }

    public InstanceState state() {
        return state;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public List<InstanceStep> steps() {
        return steps;
    }

    public List<StepDependency> dependencies() {
        return dependencies;
    }

    public record Progress(int closed, int total) {}

    public record Bottleneck(StepId step, Duration waitedFor) {}
}
