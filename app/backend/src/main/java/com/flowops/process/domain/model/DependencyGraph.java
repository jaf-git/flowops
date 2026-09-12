package com.flowops.process.domain.model;

import com.flowops.process.domain.exception.CycleWouldFormException;
import com.flowops.process.domain.exception.StepWouldBeStrandedException;
import com.flowops.process.domain.exception.UnknownStepException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DependencyGraph {
    private final Set<StepId> steps;
    private final Set<StepDependency> edges;

    private DependencyGraph(Set<StepId> steps, Set<StepDependency> edges) {
        this.steps = Set.copyOf(steps);
        this.edges = Set.copyOf(edges);
    }

    public static DependencyGraph of(Collection<StepId> steps, Collection<StepDependency> edges) {
        Set<StepId> known = new LinkedHashSet<>(steps);
        for (StepDependency edge : edges) {
            if (!known.contains(edge.dependent()) || !known.contains(edge.dependsOn())) {
                throw new UnknownStepException();
            }
        }
        return new DependencyGraph(known, new LinkedHashSet<>(edges));
    }

    public Set<StepId> entrySteps() {
        Set<StepId> waiting = new HashSet<>();
        for (StepDependency edge : edges) {
            waiting.add(edge.dependent());
        }
        Set<StepId> entries = new LinkedHashSet<>();
        for (StepId step : steps) {
            if (!waiting.contains(step)) {
                entries.add(step);
            }
        }
        return entries;
    }

    public List<StepId> cycleThrough(StepDependency edge) {
        if (edge.dependent().equals(edge.dependsOn())) {
            return List.of(edge.dependent());
        }
        List<StepId> path = pathFrom(edge.dependsOn(), edge.dependent());
        if (path.isEmpty()) {
            return List.of();
        }

        List<StepId> cycle = new ArrayList<>();
        cycle.add(edge.dependent());
        cycle.addAll(path.subList(0, path.size() - 1));
        return List.copyOf(cycle);
    }

    public Set<StepId> stepsUnreachableFromAnyEntry() {
        Set<StepId> reached = new HashSet<>(entrySteps());
        Deque<StepId> pending = new ArrayDeque<>(reached);
        while (!pending.isEmpty()) {
            StepId step = pending.removeFirst();
            for (StepDependency edge : edges) {
                if (edge.dependsOn().equals(step) && reached.add(edge.dependent())) {
                    pending.addLast(edge.dependent());
                }
            }
        }
        Set<StepId> stranded = new LinkedHashSet<>();
        for (StepId step : steps) {
            if (!reached.contains(step)) {
                stranded.add(step);
            }
        }
        return stranded;
    }

    public DependencyGraph with(StepDependency edge) {
        requireBothEndsKnown(edge);
        if (edges.contains(edge)) {
            return this;
        }
        List<StepId> cycle = cycleThrough(edge);
        if (!cycle.isEmpty()) {
            throw new CycleWouldFormException(cycle);
        }
        Set<StepDependency> widened = new LinkedHashSet<>(edges);
        widened.add(edge);
        return new DependencyGraph(steps, widened);
    }

    public DependencyGraph without(StepDependency edge) {
        Set<StepDependency> narrowed = new LinkedHashSet<>(edges);
        narrowed.remove(edge);
        DependencyGraph result = new DependencyGraph(steps, narrowed);
        result.requireValid();
        return result;
    }

    public DependencyGraph withoutStep(StepId step) {
        Set<StepId> remaining = new LinkedHashSet<>(steps);
        remaining.remove(step);
        Set<StepDependency> surviving = new LinkedHashSet<>();
        for (StepDependency edge : edges) {
            if (!edge.dependent().equals(step) && !edge.dependsOn().equals(step)) {
                surviving.add(edge);
            }
        }
        DependencyGraph result = new DependencyGraph(remaining, surviving);
        result.requireValid();
        return result;
    }

    public void requireValid() {
        Set<StepId> stranded = stepsUnreachableFromAnyEntry();
        if (stranded.isEmpty()) {
            return;
        }

        List<StepId> cycle = cycleWithin(stranded);
        if (!cycle.isEmpty()) {
            throw new CycleWouldFormException(cycle);
        }
        throw new StepWouldBeStrandedException(stranded);
    }

    private List<StepId> cycleWithin(Set<StepId> candidates) {
        for (StepId step : candidates) {
            for (StepDependency edge : edges) {
                if (!edge.dependent().equals(step)) {
                    continue;
                }
                List<StepId> back = pathFrom(edge.dependsOn(), step);
                if (!back.isEmpty()) {
                    return List.copyOf(back);
                }
            }
        }
        return List.of();
    }

    public Set<StepDependency> edges() {
        return edges;
    }

    public Set<StepId> steps() {
        return steps;
    }

    public boolean contains(StepDependency edge) {
        return edges.contains(edge);
    }

    private void requireBothEndsKnown(StepDependency edge) {
        if (!steps.contains(edge.dependent()) || !steps.contains(edge.dependsOn())) {
            throw new UnknownStepException();
        }
    }

    private List<StepId> pathFrom(StepId from, StepId to) {
        List<StepId> path = new ArrayList<>();
        if (walk(from, to, new HashSet<>(), path)) {
            return path;
        }
        return List.of();
    }

    private boolean walk(StepId at, StepId target, Set<StepId> seen, List<StepId> path) {
        if (!seen.add(at)) {
            return false;
        }
        path.add(at);
        if (at.equals(target)) {
            return true;
        }
        for (StepDependency edge : edges) {
            if (edge.dependent().equals(at) && walk(edge.dependsOn(), target, seen, path)) {
                return true;
            }
        }
        path.removeLast();
        return false;
    }
}
