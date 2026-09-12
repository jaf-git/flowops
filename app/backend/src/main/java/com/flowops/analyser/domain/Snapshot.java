package com.flowops.analyser.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record Snapshot(
        Instant from,
        Instant to,
        List<Node> nodes,
        List<Bracket> brackets,
        List<Job> jobs,
        List<Wait> waits,
        List<Template> templates,
        Shapes shapes,
        List<String> governedWorkTypes,
        List<MergedActivity> mergedActivities) {
    public Snapshot(
            Instant from,
            Instant to,
            List<Node> nodes,
            List<Bracket> brackets,
            List<Job> jobs,
            List<Wait> waits,
            List<Template> templates,
            Shapes shapes) {
        this(from, to, nodes, brackets, jobs, waits, templates, shapes, List.of(), List.of());
    }

    public Snapshot(
            Instant from,
            Instant to,
            List<Node> nodes,
            List<Bracket> brackets,
            List<Job> jobs,
            List<Wait> waits,
            List<Template> templates,
            Shapes shapes,
            List<String> governedWorkTypes) {
        this(from, to, nodes, brackets, jobs, waits, templates, shapes, governedWorkTypes, List.of());
    }

    public Snapshot {
        governedWorkTypes = governedWorkTypes == null ? List.of() : List.copyOf(governedWorkTypes);
        mergedActivities = mergedActivities == null ? List.of() : List.copyOf(mergedActivities);
        Objects.requireNonNull(from, "a snapshot with no window cannot say what it held");
        Objects.requireNonNull(to, "a snapshot with no window cannot say what it held");
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("a window runs forwards: " + from + " to " + to);
        }
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        brackets = brackets == null ? List.of() : List.copyOf(brackets);
        jobs = jobs == null ? List.of() : List.copyOf(jobs);
        waits = waits == null ? List.of() : List.copyOf(waits);
        templates = templates == null ? List.of() : List.copyOf(templates);
        shapes = shapes == null ? Shapes.none() : shapes;
    }

    public record Node(
            String id,
            String jobId,
            String workType,
            String title,
            String detail,
            String text,
            String state,
            String outputType,
            String performerRoleId,
            Instant createdAt,
            Instant closedAt,
            List<Move> moves) {
        public Node {
            moves = moves == null ? List.of() : List.copyOf(moves);
        }

        public boolean isEnriched() {
            return title != null && !title.isBlank();
        }

        public boolean hasATrail() {
            return !moves.isEmpty();
        }

        public long timesItReached(String state) {
            return moves.stream().filter(move -> state.equals(move.to())).count();
        }
    }

    public record Move(String from, String to, String actorId, Instant occurredAt) {}

    public record Bracket(
            String id,
            String jobId,
            String workType,
            String closeKind,
            String outputKind,
            Instant openedAt,
            Instant closedAt) {
        public BracketOutcome outcome() {
            return BracketOutcome.of(closeKind);
        }
    }

    public record Job(
            String id,
            String name,
            String status,
            String counterpartyName,
            String projectLabel,
            Instant openedAt,
            Instant closedAt) {}

    public record Wait(
            String id, String bracketId, String waitingOn, Instant openedAt, Instant satisfiedAt, Instant cancelledAt) {
        public boolean satisfied() {
            return satisfiedAt != null;
        }

        public boolean cancelled() {
            return cancelledAt != null;
        }
    }

    public record Shapes(int clustered, int excludedAsChurn, List<Shape> kinds, List<Process> processes) {
        public Shapes {
            kinds = kinds == null ? List.of() : List.copyOf(kinds);
            processes = processes == null ? List.of() : List.copyOf(processes);
        }

        public static Shapes none() {
            return new Shapes(0, 0, List.of(), List.of());
        }

        public boolean ran() {
            return clustered > 0;
        }
    }

    public record Shape(
            String id,
            String workType,
            boolean subprocess,
            List<String> nodeIds,
            List<String> jobIds,
            double cohesion,
            double certainty,
            String activityName) {
        public Shape(
                String id,
                String workType,
                boolean subprocess,
                List<String> nodeIds,
                List<String> jobIds,
                double cohesion,
                double certainty) {
            this(id, workType, subprocess, nodeIds, jobIds, cohesion, certainty, null);
        }

        public Shape {
            nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds);
            jobIds = jobIds == null ? List.of() : List.copyOf(jobIds);
        }
    }

    public record Process(
            List<String> steps,
            List<String> order,
            boolean orderReliable,
            double orderConfidence,
            List<String> jobIds,
            int runs,
            double certainty) {
        public Process {
            steps = steps == null ? List.of() : List.copyOf(steps);
            order = order == null ? List.of() : List.copyOf(order);
            jobIds = jobIds == null ? List.of() : List.copyOf(jobIds);
        }
    }

    /**
     * An activity a person merged into another, and the one it became.
     *
     * <p>Here so an analyser can notice what a merge left in the library on every run, not only at
     * the moment somebody pressed the button. A merge made in a workspace that had never completed an
     * analysis has no run to attach a finding to; this is how that one is caught later.
     */
    public record MergedActivity(String name, String survivingName) {}

    public record Template(String id, String title, String workType, String status, int timesUsed) {
        public boolean hasMatchedWork() {
            return timesUsed > 0;
        }
    }
}
