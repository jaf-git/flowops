package com.flowops.discovery.domain.model;

import com.flowops.discovery.domain.enums.Direction;
import com.flowops.discovery.domain.enums.NodeKind;
import com.flowops.discovery.domain.enums.OutputType;
import com.flowops.discovery.domain.enums.SubjectSource;
import com.flowops.discovery.domain.enums.WorkNodeState;
import com.flowops.discovery.domain.exception.IllegalNodeTransitionException;
import com.flowops.discovery.domain.exception.NotYoursToDescribeException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class WorkNode {
    private final WorkNodeId id;
    private final String text;
    private final UUID creatorId;
    private final UUID creatorRoleId;
    private final NodeKind kind;
    private final Instant createdAt;

    private JobId job;
    private SubjectSource subjectSource;
    private TrackId track;
    private WorkNodeState state;
    private Direction direction;
    private UUID performerId;
    private UUID performerRoleId;
    private OutputType outputType;
    private Instant startedAt;
    private Instant closedAt;
    private Instant nudgedAt;
    private Fingerprint fingerprint;
    private BigDecimal weight;
    private UUID taskTemplateId;
    private String workType;

    private String title;
    private String detail;
    private List<String> checklist;

    private UUID enrichedBy;

    private final List<NodeStateTransition> pendingTransitions = new ArrayList<>();

    private WorkNode(
            WorkNodeId id,
            JobId job,
            String text,
            UUID creatorId,
            UUID creatorRoleId,
            NodeKind kind,
            Instant createdAt,
            WorkNodeState state,
            Direction direction) {
        this.id = Objects.requireNonNull(id);
        this.job = Objects.requireNonNull(job);
        this.text = Objects.requireNonNull(text);
        this.creatorId = Objects.requireNonNull(creatorId);
        this.creatorRoleId = creatorRoleId;
        this.kind = Objects.requireNonNull(kind);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.state = state;
        this.direction = direction;
    }

    public static WorkNode marked(
            WorkNodeId id,
            JobId job,
            String text,
            UUID creatorId,
            UUID creatorRoleId,
            NodeKind kind,
            Direction direction,
            Instant at) {
        Objects.requireNonNull(direction, "a node without a direction cannot be threaded or paired");
        WorkNode node =
                new WorkNode(id, job, text, creatorId, creatorRoleId, kind, at, WorkNodeState.MARKED, direction);

        if (direction == Direction.QUERY) {
            node.state = WorkNodeState.QUERY;
        }

        node.pendingTransitions.add(new NodeStateTransition(null, node.state, creatorId, null));
        return node;
    }

    public static WorkNode rehydrated(
            WorkNodeId id,
            JobId job,
            String text,
            UUID creatorId,
            UUID creatorRoleId,
            NodeKind kind,
            Instant createdAt,
            WorkNodeState state,
            Direction direction,
            TrackId track,
            SubjectSource subjectSource,
            UUID performerId,
            UUID performerRoleId,
            OutputType outputType,
            Instant startedAt,
            Instant closedAt,
            Instant nudgedAt,
            Fingerprint fingerprint,
            BigDecimal weight,
            UUID taskTemplateId,
            String workType,
            String title,
            String detail,
            List<String> checklist,
            UUID enrichedBy) {
        WorkNode node = new WorkNode(
                id,
                job,
                text,
                creatorId,
                creatorRoleId,
                kind,
                createdAt,
                Objects.requireNonNull(state, "a node row without a state cannot be read back safely"),
                Objects.requireNonNull(direction, "a node without a direction cannot be threaded or paired"));
        node.track = track;
        node.subjectSource = subjectSource;
        node.performerId = performerId;
        node.performerRoleId = performerRoleId;
        node.outputType = outputType;
        node.startedAt = startedAt;
        node.closedAt = closedAt;
        node.nudgedAt = nudgedAt;
        node.fingerprint = fingerprint;
        node.weight = weight;
        node.taskTemplateId = taskTemplateId;
        node.workType = workType;
        node.title = title;
        node.detail = detail;
        node.checklist = checklist == null ? null : List.copyOf(checklist);
        node.enrichedBy = enrichedBy;

        return node;
    }

    public void requestedOf(UUID performer, UUID performerRole) {
        require(WorkNodeState.MARKED, WorkNodeState.ASSIGNED);
        this.performerId = Objects.requireNonNull(performer, "a request needs somebody to do it");
        this.performerRoleId = performerRole;

        moveTo(WorkNodeState.ASSIGNED, creatorId);
    }

    public void keptForSelf() {
        require(WorkNodeState.MARKED, WorkNodeState.SELF);
        this.performerId = creatorId;
        this.performerRoleId = creatorRoleId;
        moveTo(WorkNodeState.SELF, creatorId);
    }

    public void started(Instant at) {
        requireOneOf(WorkNodeState.IN_PROGRESS, WorkNodeState.ASSIGNED, WorkNodeState.SELF);
        this.startedAt = Objects.requireNonNull(at, "work that began, began at a time");
        moveTo(WorkNodeState.IN_PROGRESS, performerId);
    }

    public void blocked() {
        require(WorkNodeState.IN_PROGRESS, WorkNodeState.BLOCKED);
        moveTo(WorkNodeState.BLOCKED, performerId);
    }

    public void resumed() {
        require(WorkNodeState.BLOCKED, WorkNodeState.IN_PROGRESS);
        moveTo(WorkNodeState.IN_PROGRESS, performerId);
    }

    public void completed(OutputType output) {
        Objects.requireNonNull(
                output,
                "a completion with no output answer is refused by machine 4.1; a guessed output type is worse than a "
                        + "missing one because nothing downstream can detect it");
        require(WorkNodeState.IN_PROGRESS, WorkNodeState.COMPLETED);
        this.outputType = output;

        if (output.closesTheNode()) {
            moveTo(WorkNodeState.COMPLETED, performerId);
        }
    }

    public void closed(Instant at) {
        require(WorkNodeState.COMPLETED, WorkNodeState.CLOSED);
        if (outputType == null || !outputType.closesTheNode()) {
            throw new IllegalStateException("node " + id.value()
                    + " has no output answer yet; 'no output yet' keeps the work open rather than closing it emptily");
        }
        this.closedAt = Objects.requireNonNull(at, "a node that closed, closed at a time");
        moveTo(WorkNodeState.CLOSED, performerId);
    }

    public void lapsed() {
        lapsed(null);
    }

    public void lapsed(UUID actor) {
        requireOneOf(
                WorkNodeState.LAPSED,
                WorkNodeState.ASSIGNED,
                WorkNodeState.IN_PROGRESS,
                WorkNodeState.SELF,
                WorkNodeState.BLOCKED);
        moveTo(WorkNodeState.LAPSED, actor);
    }

    public void bounced() {
        require(WorkNodeState.ASSIGNED, WorkNodeState.BOUNCED);

        moveTo(WorkNodeState.BOUNCED, performerId);
    }

    public void reassignedTo(UUID performer, UUID performerRole) {
        require(WorkNodeState.BOUNCED, WorkNodeState.ASSIGNED);
        this.performerId = Objects.requireNonNull(performer, "reassigned work needs somebody to do it");
        this.performerRoleId = performerRole;
        moveTo(WorkNodeState.ASSIGNED, performer);
    }

    public void becameQuery() {
        requireOneOf(WorkNodeState.QUERY, WorkNodeState.MARKED, WorkNodeState.ASSIGNED, WorkNodeState.SELF);
        this.direction = Direction.QUERY;
        this.track = null;

        moveTo(WorkNodeState.QUERY, creatorId);
    }

    public void answered() {
        require(WorkNodeState.QUERY, WorkNodeState.ANSWERED);

        moveTo(WorkNodeState.ANSWERED, null);
    }

    public void reopenedByLoop() {
        require(WorkNodeState.COMPLETED, WorkNodeState.IN_PROGRESS);

        moveTo(WorkNodeState.IN_PROGRESS, null);
    }

    public void nudged(Instant at) {
        Objects.requireNonNull(at, "a nudge happened at a time");
        if (nudgedAt != null) {
            throw new IllegalStateException("node " + id.value() + " was already nudged at " + nudgedAt
                    + "; a second nudge teaches people to ignore the first");
        }
        this.nudgedAt = at;
    }

    public void subjectSetTo(JobId subject, SubjectSource source) {
        this.job = Objects.requireNonNull(subject, "a unit of work belongs to an engagement somebody can point at");
        this.subjectSource = Objects.requireNonNull(source, "a guess and a person's answer are not the same evidence");
    }

    public void fingerprintedAs(Fingerprint print) {
        refuseIfQuery("a fingerprint");
        this.fingerprint = Objects.requireNonNull(print);
    }

    public void weightedAt(BigDecimal value) {
        refuseIfQuery("a weight");
        this.weight = Objects.requireNonNull(value, "a weight nobody computed is not a weight");
    }

    public void formalisedAs(UUID taskTemplate) {
        Objects.requireNonNull(taskTemplate, "a formalisation points at the template a person saved");
        if (taskTemplateId != null) {
            throw new IllegalStateException("node " + id.value() + " was already formalised as template "
                    + taskTemplateId + "; a second template from one observation is a duplicate, not a correction");
        }
        this.taskTemplateId = taskTemplate;
    }

    public void placedIn(TrackId trackId) {
        if (!direction.canJoinATrack()) {
            throw new IllegalStateException("a " + direction + " node never opens or joins a track — invariant I11");
        }
        this.track = Objects.requireNonNull(trackId);
    }

    public boolean isOrphan() {
        return track == null && direction.canJoinATrack();
    }

    public boolean isQuery() {
        return state == WorkNodeState.QUERY || state == WorkNodeState.ANSWERED || direction == Direction.QUERY;
    }

    public boolean hasBeenNudged() {
        return nudgedAt != null;
    }

    public boolean contributesADuration() {
        return !isQuery() && state != WorkNodeState.LAPSED;
    }

    private void refuseIfQuery(String what) {
        if (isQuery()) {
            throw new IllegalStateException("node " + id.value() + " is a question, and a question never receives "
                    + what + "; invariant I11 removes the route rather than filtering afterwards");
        }
    }

    private void moveTo(WorkNodeState to, UUID actor) {
        pendingTransitions.add(new NodeStateTransition(state, to, actor, null));
        this.state = to;
    }

    public List<NodeStateTransition> pendingTransitions() {
        return Collections.unmodifiableList(new ArrayList<>(pendingTransitions));
    }

    public void transitionsWritten() {
        pendingTransitions.clear();
    }

    public boolean enrichedWith(UUID actor, String title, String detail, List<String> checklist) {
        Objects.requireNonNull(actor, "a description is somebody's, and an anonymous one cannot be corrected");
        refuseUnlessMineToDescribe(actor);

        boolean changed = false;
        changed |= describing(actor, hasText(title), this.title != null, "title");
        changed |= describing(actor, hasText(detail), this.detail != null, "detail");
        changed |= describing(actor, checklist != null, this.checklist != null, "checklist");

        if (hasText(title)) {
            this.title = title.strip();
        }
        if (hasText(detail)) {
            this.detail = detail.strip();
        }
        if (checklist != null) {
            this.checklist = List.copyOf(checklist);
        }

        if (changed && enrichedBy == null) {
            this.enrichedBy = actor;
        }
        return changed;
    }

    private boolean describing(UUID actor, boolean offered, boolean alreadyAnswered, String field) {
        if (!offered) {
            return false;
        }
        if (alreadyAnswered) {
            refuseUnlessMineToCorrect(actor, field);
        }
        return true;
    }

    private void refuseUnlessMineToDescribe(UUID actor) {
        boolean mine = actor.equals(creatorId) || actor.equals(performerId);
        if (!mine) {
            throw new NotYoursToDescribeException("node " + id.value()
                    + " was marked by somebody else and is being done by somebody else; a title flows into a "
                    + "template draft everyone reads, so the person who clicked owns the record");
        }
    }

    private void refuseUnlessMineToCorrect(UUID actor, String field) {
        if (isSettled()) {
            throw new NotYoursToDescribeException("node " + id.value() + " has settled, so its " + field
                    + " is evidence now; evidence that can be reworded afterwards is evidence nobody can rely on");
        }
        if (enrichedBy != null && !enrichedBy.equals(actor)) {
            throw new NotYoursToDescribeException("the " + field + " on node " + id.value()
                    + " was written by somebody else; an empty field is anybody's to fill, and an answered one is "
                    + "its author's to correct");
        }
    }

    public boolean isSettled() {
        return state == WorkNodeState.CLOSED || state == WorkNodeState.LAPSED || state == WorkNodeState.ANSWERED;
    }

    public Optional<UUID> enrichedBy() {
        return Optional.ofNullable(enrichedBy);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public Optional<String> title() {
        return Optional.ofNullable(title);
    }

    public Optional<String> detail() {
        return Optional.ofNullable(detail);
    }

    public Optional<List<String>> checklist() {
        return Optional.ofNullable(checklist);
    }

    private void require(WorkNodeState from, WorkNodeState to) {
        if (state != from) {
            throw new IllegalNodeTransitionException(state, to);
        }
    }

    private void requireOneOf(WorkNodeState to, WorkNodeState... from) {
        for (WorkNodeState candidate : from) {
            if (state == candidate) {
                return;
            }
        }
        throw new IllegalNodeTransitionException(state, to);
    }

    public WorkNodeId id() {
        return id;
    }

    public JobId job() {
        return job;
    }

    public Optional<SubjectSource> subjectSource() {
        return Optional.ofNullable(subjectSource);
    }

    public String text() {
        return text;
    }

    public UUID creatorId() {
        return creatorId;
    }

    public Optional<UUID> creatorRoleId() {
        return Optional.ofNullable(creatorRoleId);
    }

    public Optional<UUID> performerId() {
        return Optional.ofNullable(performerId);
    }

    public Optional<UUID> performerRoleId() {
        return Optional.ofNullable(performerRoleId);
    }

    public Optional<TrackId> track() {
        return Optional.ofNullable(track);
    }

    public WorkNodeState state() {
        return state;
    }

    public Direction direction() {
        return direction;
    }

    public NodeKind kind() {
        return kind;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<OutputType> outputType() {
        return Optional.ofNullable(outputType);
    }

    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    public Optional<Instant> closedAt() {
        return Optional.ofNullable(closedAt);
    }

    public Optional<Instant> nudgedAt() {
        return Optional.ofNullable(nudgedAt);
    }

    public Optional<Fingerprint> fingerprint() {
        return Optional.ofNullable(fingerprint);
    }

    public Optional<BigDecimal> weight() {
        return Optional.ofNullable(weight);
    }

    public Optional<UUID> taskTemplateId() {
        return Optional.ofNullable(taskTemplateId);
    }

    public Optional<String> workType() {
        return Optional.ofNullable(workType);
    }
}
