package com.flowops.nodepipeline.domain.compose;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProcessValidator {
    private ProcessValidator() {}

    public record Result(List<String> errors, List<String> warnings) {
        public Result {
            errors = errors == null ? List.of() : List.copyOf(errors);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        public boolean isAllowed() {
            return errors.isEmpty();
        }
    }

    public static Result validate(DraftProcess process, String forStatus) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Set<String> stepIds = new LinkedHashSet<>();
        process.steps().forEach(step -> stepIds.add(step.id()));

        if (process.steps().isEmpty()) {
            errors.add("a process with no steps is not a process");
        }

        for (DraftProcess.DraftStep step : process.steps()) {
            if (step.taskTemplateId() == null || step.taskTemplateStatus() == null) {
                errors.add("step " + step.id() + " points at a task template that does not exist");
                continue;
            }
            if ("RETIRED".equals(step.taskTemplateStatus())) {
                errors.add("step " + step.id() + " uses a retired task template");
                continue;
            }

            if ("APPROVED".equals(forStatus) && !"APPROVED".equals(step.taskTemplateStatus())) {
                errors.add("step " + step.id() + " uses task template " + step.taskTemplateId() + " which is "
                        + step.taskTemplateStatus() + " — a process cannot be approved on unapproved steps");
            }
        }

        for (DraftProcess.DraftEdge edge : process.edges()) {
            if (!stepIds.contains(edge.dependentStepId()) || !stepIds.contains(edge.dependsOnStepId())) {
                errors.add("a dependency points outside this process");
            }
            if (edge.dependentStepId().equals(edge.dependsOnStepId())) {
                errors.add("a step cannot depend on itself");
            }
        }

        Map<String, List<String>> waitsFor = new LinkedHashMap<>();
        for (DraftProcess.DraftEdge edge : process.blockingEdges()) {
            waitsFor.computeIfAbsent(edge.dependentStepId(), key -> new ArrayList<>())
                    .add(edge.dependsOnStepId());
        }

        if (hasCycle(stepIds, waitsFor)) {
            errors.add("the confirmed dependencies form a cycle");
        }

        if (!process.blockingEdges().isEmpty()) {
            boolean anyEntry = process.steps().stream().anyMatch(step -> waitsFor.getOrDefault(step.id(), List.of())
                    .isEmpty());
            if (!anyEntry) {
                errors.add("every step waits for another — nothing can start");
            }
        }

        if ("COMPOSED_FROM_DISCOVERY".equals(process.origin())
                && (process.evidence() == null || process.evidence().jobIds().isEmpty())) {
            errors.add("a composed process must carry the jobs it was composed from");
        }

        Map<String, DraftProcess.DraftStep> byId = new LinkedHashMap<>();
        process.steps().forEach(step -> byId.put(step.id(), step));
        for (DraftProcess.DraftEdge edge : process.blockingEdges()) {
            DraftProcess.DraftStep dependent = byId.get(edge.dependentStepId());
            DraftProcess.DraftStep dependedOn = byId.get(edge.dependsOnStepId());
            if (dependent != null
                    && dependedOn != null
                    && dependent.lane() == dependedOn.lane()
                    && dependent.position() < dependedOn.position()) {
                warnings.add(edge.dependentStepId() + " waits for a later step in the same lane");
            }
        }

        if ("APPROVED".equals(forStatus)
                && !process.observedEdges().isEmpty()
                && process.blockingEdges().isEmpty()) {
            warnings.add("all order is observed, none confirmed — every step will be reachable at once");
        }

        Set<String> seenTemplates = new HashSet<>();
        for (DraftProcess.DraftStep step : process.steps()) {
            if (!seenTemplates.add(step.taskTemplateId())
                    && (step.label() == null || step.label().isBlank())) {
                warnings.add("step " + step.id() + " repeats task template " + step.taskTemplateId()
                        + " with no label — a reader cannot tell the two occurrences apart");
            }
        }

        return new Result(errors, warnings);
    }

    private static boolean hasCycle(Set<String> steps, Map<String, List<String>> waitsFor) {
        Set<String> settled = new HashSet<>();
        Set<String> onPath = new LinkedHashSet<>();

        for (String start : steps) {
            if (settled.contains(start)) {
                continue;
            }
            java.util.Deque<String> stack = new java.util.ArrayDeque<>();
            stack.push(start);

            while (!stack.isEmpty()) {
                String current = stack.peek();
                if (!onPath.contains(current)) {
                    onPath.add(current);
                }
                boolean descended = false;
                for (String next : waitsFor.getOrDefault(current, List.of())) {
                    if (onPath.contains(next)) {
                        return true;
                    }
                    if (!settled.contains(next)) {
                        stack.push(next);
                        descended = true;
                        break;
                    }
                }
                if (!descended) {
                    settled.add(current);
                    onPath.remove(current);
                    stack.pop();
                }
            }
        }
        return false;
    }
}
