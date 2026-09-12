package com.flowops.discovery.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.CloseReason;
import com.flowops.discovery.domain.enums.Completeness;
import com.flowops.discovery.domain.enums.Direction;
import com.flowops.discovery.domain.enums.EvidenceOrigin;
import com.flowops.discovery.domain.enums.KeyBasis;
import com.flowops.discovery.domain.enums.NodeKind;
import com.flowops.discovery.domain.enums.OutputType;
import com.flowops.discovery.domain.enums.SubjectSource;
import com.flowops.discovery.domain.enums.TrackState;
import com.flowops.discovery.domain.enums.WorkNodeState;
import com.flowops.discovery.domain.model.Fingerprint;
import com.flowops.discovery.domain.model.Job;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.NodeStateTransition;
import com.flowops.discovery.domain.model.Track;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.TrackKey;
import com.flowops.discovery.domain.model.WorkNode;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkGraphPersistenceAdapter implements WorkGraphPort {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ObjectMapper json;

    public WorkGraphPersistenceAdapter(JdbcTemplate jdbc, Clock clock, ObjectMapper json) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.json = json;
    }

    private static final String JOB_COLUMNS =
            "id, name, status, standing, opened_at, closed_at, opened_by, last_activity_at, "
                    + "close_reason, shape_eligible, counterparty_id, project_label, is_rework, rework_of_job_id";

    private static final String JOB_COLUMNS_ALIASED =
            """
            j.id, j.name, j.status, j.standing, j.opened_at, j.closed_at, j.opened_by, j.last_activity_at,
            j.close_reason, j.shape_eligible, j.counterparty_id, j.project_label, j.is_rework, j.rework_of_job_id
            """;

    private static final String ENDED_STATES = "('CLOSED', 'AUTO_CLOSED', 'FORCE_CLOSED')";

    private static final String TRACK_COLUMNS =
            """
            id, job_id, from_role_id, to_role_id, performer_id, solo, key_basis, state,
            opened_at, closed_at, close_reason, disrupted, completeness, last_activity_at,
            continues_track_id, track_type_id, process_template_id
            """;

    private static final String NODE_COLUMNS =
            """
            id, job_id, track_id, text, creator_id, creator_role_id, performer_id, performer_role_id,
            created_at, started_at, closed_at, state, direction, output_type, kind,
            subject_source, nudged_at, weight, task_template_id, fingerprint,
            fingerprint_performer_role_id, fingerprint_median_work_ms, fingerprint_output_type,
            fingerprint_preceding_role_id, fingerprint_preceding_direction,
            fingerprint_following_role_id, fingerprint_position_in_track,
            work_type,
            title, detail, checklist, enriched_by
            """;

    private static final String SAVE_JOB =
            """
            insert into job (id, name, status, standing, opened_at, closed_at, opened_by, last_activity_at,
                             close_reason, shape_eligible, counterparty_id, project_label,
                             is_rework, rework_of_job_id)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (id) do update set
                name = excluded.name,
                status = excluded.status,
                standing = excluded.standing,
                closed_at = excluded.closed_at,
                last_activity_at = excluded.last_activity_at,
                close_reason = excluded.close_reason,
                shape_eligible = excluded.shape_eligible,
                counterparty_id = excluded.counterparty_id,
                project_label = excluded.project_label,
                is_rework = excluded.is_rework,
                rework_of_job_id = excluded.rework_of_job_id
            """;

    @Override
    public void save(Job job) {
        jdbc.update(
                SAVE_JOB,
                job.id().value(),
                job.name(),
                job.status().name(),
                job.standing(),
                Timestamp.from(job.openedAt()),
                job.closedAt().map(Timestamp::from).orElse(null),
                job.openedBy(),
                Timestamp.from(job.lastActivityAt()),
                job.closeReason().orElse(null),
                job.shapeEligible(),
                job.counterpartyId().orElse(null),
                job.projectLabel().orElse(null),
                job.isRework(),
                job.reworkOfJobId().map(JobId::value).orElse(null));
    }

    private static final String FIND_JOB = "select " + JOB_COLUMNS + " from job where id = ?";

    @Override
    public Optional<Job> findJob(JobId id) {
        return jdbc.query(FIND_JOB, (row, index) -> job(row), id.value()).stream()
                .findFirst();
    }

    @Override
    public JobId nextJobId() {
        return JobId.of(UUID.randomUUID());
    }

    @Override
    public TrackId nextTrackId() {
        return TrackId.of(UUID.randomUUID());
    }

    private static final String SAVE_TRACK =
            """
            insert into track (id, job_id, from_role_id, to_role_id, performer_id, solo, key_basis,
                               state, opened_at, closed_at, close_reason, disrupted, completeness,
                               last_activity_at, continues_track_id, track_type_id, process_template_id)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (id) do update set
                performer_id = excluded.performer_id,
                state = excluded.state,
                closed_at = excluded.closed_at,
                close_reason = coalesce(track.close_reason, excluded.close_reason),
                disrupted = track.disrupted or excluded.disrupted,
                completeness = coalesce(track.completeness, excluded.completeness),
                last_activity_at = excluded.last_activity_at,
                continues_track_id = coalesce(track.continues_track_id, excluded.continues_track_id),
                track_type_id = excluded.track_type_id,
                process_template_id = coalesce(track.process_template_id, excluded.process_template_id)
            """;

    @Override
    public void save(Track track) {
        TrackKey key = track.key();
        jdbc.update(
                SAVE_TRACK,
                track.id().value(),
                track.job().value(),
                key.fromRoleId(),
                key.toRoleId(),
                track.performerId(),
                key.isSolo(),
                key.basis().name(),
                track.state().name(),
                Timestamp.from(track.openedAt()),
                track.closedAt().map(Timestamp::from).orElse(null),
                track.closeReason().map(Enum::name).orElse(null),
                track.isDisrupted(),
                track.completeness().map(Enum::name).orElse(null),
                Timestamp.from(track.lastActivityAt()),
                track.continuesTrackId().map(TrackId::value).orElse(null),
                track.trackTypeId().orElse(null),
                track.processTemplateId().orElse(null));
    }

    private static final String FIND_OPEN_TRACK =
            """
            select %s
            from track
            where job_id = ?
              and from_role_id is not distinct from ?::uuid
              and to_role_id is not distinct from ?::uuid
              and performer_id = ?
              and closed_at is null
            order by last_activity_at desc
            limit 1
            """
                    .formatted(TRACK_COLUMNS);

    @Override
    public Optional<Track> findOpenTrack(JobId job, TrackKey key) {
        return jdbc
                .query(
                        FIND_OPEN_TRACK,
                        (row, index) -> track(row),
                        job.value(),
                        key.fromRoleId(),
                        key.toRoleId(),
                        key.performerId())
                .stream()
                .findFirst();
    }

    private static final String FIND_OPEN_TRACK_FOR_PERFORMER =
            """
            select %s
            from track
            where job_id = ?
              and performer_id = ?
              and closed_at is null
            order by last_activity_at desc
            limit 1
            """
                    .formatted(TRACK_COLUMNS);

    @Override
    public Optional<Track> findOpenTrackForPerformer(JobId job, UUID performer) {
        return jdbc.query(FIND_OPEN_TRACK_FOR_PERFORMER, (row, index) -> track(row), job.value(), performer).stream()
                .findFirst();
    }

    private static final String FIND_TRACK = "select %s from track where id = ?".formatted(TRACK_COLUMNS);

    @Override
    public Optional<Track> findTrack(TrackId id) {
        return jdbc.query(FIND_TRACK, (row, index) -> track(row), id.value()).stream()
                .findFirst();
    }

    private static final String SAVE_NODE =
            """
            insert into work_node (id, job_id, track_id, text, creator_id, creator_role_id,
                                   performer_id, performer_role_id, created_at, started_at, closed_at,
                                   state, direction, output_type, kind,
                                   subject_source, nudged_at, weight, task_template_id,
                                   fingerprint,
                                   fingerprint_performer_role_id, fingerprint_median_work_ms,
                                   fingerprint_output_type, fingerprint_preceding_role_id,
                                   fingerprint_preceding_direction, fingerprint_following_role_id,
                                   fingerprint_position_in_track,
                                   title, detail, checklist, enriched_by)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, cast(? as jsonb), ?)
            on conflict (id) do update set
                job_id = excluded.job_id,
                track_id = excluded.track_id,
                performer_id = excluded.performer_id,
                performer_role_id = excluded.performer_role_id,
                started_at = coalesce(work_node.started_at, excluded.started_at),
                closed_at = excluded.closed_at,
                state = excluded.state,
                direction = excluded.direction,
                output_type = excluded.output_type,
                subject_source = excluded.subject_source,
                nudged_at = coalesce(work_node.nudged_at, excluded.nudged_at),
                weight = excluded.weight,
                task_template_id = coalesce(work_node.task_template_id, excluded.task_template_id),
                fingerprint = excluded.fingerprint,
                fingerprint_performer_role_id = excluded.fingerprint_performer_role_id,
                fingerprint_median_work_ms = excluded.fingerprint_median_work_ms,
                fingerprint_output_type = excluded.fingerprint_output_type,
                fingerprint_preceding_role_id = excluded.fingerprint_preceding_role_id,
                fingerprint_preceding_direction = excluded.fingerprint_preceding_direction,
                fingerprint_following_role_id = excluded.fingerprint_following_role_id,
                fingerprint_position_in_track = excluded.fingerprint_position_in_track,

                title = excluded.title,
                detail = excluded.detail,
                checklist = excluded.checklist,

                enriched_by = coalesce(work_node.enriched_by, excluded.enriched_by)
            """;

    @Override
    public void save(WorkNode node) {
        Fingerprint print = node.fingerprint().orElse(null);
        jdbc.update(
                SAVE_NODE,
                node.id().value(),
                node.job().value(),
                node.track().map(TrackId::value).orElse(null),
                node.text(),
                node.creatorId(),
                node.creatorRoleId().orElse(null),
                node.performerId().orElse(null),
                node.performerRoleId().orElse(null),
                Timestamp.from(node.createdAt()),
                node.startedAt().map(Timestamp::from).orElse(null),
                node.closedAt().map(Timestamp::from).orElse(null),
                node.state().name(),
                node.direction().name(),
                node.outputType().map(Enum::name).orElse(null),
                node.kind().name(),
                node.subjectSource().map(Enum::name).orElse(null),
                node.nudgedAt().map(Timestamp::from).orElse(null),
                node.weight().orElse(BigDecimal.ONE),
                node.taskTemplateId().orElse(null),
                print == null ? null : print.canonical(),
                print == null ? null : print.performerRoleId(),
                print == null || print.medianWorkPhase() == null
                        ? null
                        : print.medianWorkPhase().toMillis(),
                print == null || print.outputType() == null
                        ? null
                        : print.outputType().name(),
                print == null ? null : print.precedingRoleId(),
                print == null || print.precedingDirection() == null
                        ? null
                        : print.precedingDirection().name(),
                print == null ? null : print.followingRoleId(),
                print == null ? null : print.positionInTrack(),
                node.title().orElse(null),
                node.detail().orElse(null),
                node.checklist().map(this::writeChecklist).orElse(null),
                node.enrichedBy().orElse(null));
        appendTrail(node);
    }

    private static final String APPEND_TRANSITION =
            """
            insert into work_node_state_transition
                (id, node_id, from_state, to_state, actor_user_id, reason, occurred_at, seq)
            values (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private void appendTrail(WorkNode node) {
        List<NodeStateTransition> moves = node.pendingTransitions();
        if (moves.isEmpty()) {
            return;
        }
        Timestamp now = Timestamp.from(clock.instant());
        for (int seq = 0; seq < moves.size(); seq++) {
            NodeStateTransition move = moves.get(seq);
            jdbc.update(
                    APPEND_TRANSITION,
                    UUID.randomUUID(),
                    node.id().value(),
                    move.cameFrom().map(Enum::name).orElse(null),
                    move.to().name(),
                    move.actor().orElse(null),
                    move.statedReason().orElse(null),
                    now,
                    seq);
        }
        node.transitionsWritten();
    }

    private String writeChecklist(List<String> checklist) {
        try {
            return json.writeValueAsString(checklist);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "a checklist of plain strings could not be written for node " + checklist, failure);
        }
    }

    private List<String> readChecklist(String stored) throws SQLException {
        if (stored == null) {
            return null;
        }
        try {
            return json.readValue(stored, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException failure) {
            throw new SQLException("stored checklist is not a list of strings", failure);
        }
    }

    private static final String FIND_NODE = "select %s from work_node where id = ?".formatted(NODE_COLUMNS);

    private static final String FIND_MARK_OF =
            """
            select %s from work_node
            where id in (select e.work_node_id from work_node_evidence e where e.message_id = ?)
              and job_id = ?
              and performer_id is not distinct from ?
              and kind = ?
            order by created_at, id
            limit 1
            """
                    .formatted(NODE_COLUMNS);

    @Override
    public Optional<WorkNode> findNode(WorkNodeId id) {
        return jdbc.query(FIND_NODE, (row, index) -> node(row), id.value()).stream()
                .findFirst();
    }

    private static final String NODES_OF_TRACK =
            "select %s from work_node where track_id = ? order by created_at, id".formatted(NODE_COLUMNS);

    @Override
    public List<WorkNode> nodesOf(TrackId track) {
        return jdbc.query(NODES_OF_TRACK, (row, index) -> node(row), track.value());
    }

    private static final String ORPHANS_OF_JOB =
            """
            select %s
            from work_node
            where job_id = ?
              and track_id is null
              and direction <> 'QUERY'
            order by created_at
            """
                    .formatted(NODE_COLUMNS);

    @Override
    public List<WorkNode> orphansOf(JobId job) {
        return jdbc.query(ORPHANS_OF_JOB, (row, index) -> node(row), job.value());
    }

    private static final String DELETE_NODE = "delete from work_node where id = ?";

    @Override
    public void deleteNode(WorkNodeId id) {
        jdbc.update(DELETE_NODE, id.value());
    }

    private static final String DELETE_TRACK = "delete from track where id = ?";

    @Override
    public void deleteTrack(TrackId id) {
        jdbc.update(DELETE_TRACK, id.value());
    }

    private static final String DELETE_JOB = "delete from job where id = ?";

    @Override
    public void deleteJob(JobId id) {
        jdbc.update(DELETE_JOB, id.value());
    }

    private static final String HOLDS_OTHER_WORK =
            """
            select exists (select 1 from work_node where job_id = ? and id <> ?)
                or exists (select 1 from track where job_id = ?)
            """;

    @Override
    public boolean holdsOtherWork(JobId job, WorkNodeId excluding) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(HOLDS_OTHER_WORK, Boolean.class, job.value(), excluding.value(), job.value()));
    }

    @Override
    public boolean holdsOtherNodes(JobId job, WorkNodeId excluding) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from work_node where job_id = ? and id <> ?)",
                Boolean.class,
                job.value(),
                excluding.value()));
    }

    private static final String EVIDENCE_MESSAGE_SENT_AT =
            """
            select m.sent_at
            from work_node_evidence e
            join message m on m.id = e.message_id
            where e.work_node_id = ?
            order by case when e.origin in ('PRIMARY', 'SPLIT') then 0 else 1 end, m.sent_at
            limit 1
            """;

    @Override
    public Optional<Instant> evidenceMessageSentAt(WorkNodeId id) {
        return jdbc
                .query(EVIDENCE_MESSAGE_SENT_AT, (row, index) -> instant(row.getTimestamp("sent_at")), id.value())
                .stream()
                .findFirst();
    }

    private static final String OPEN_JOBS_TOUCHED_IN =
            """
            select %s
            from job j
            where j.status not in %s
              and exists (
                  select 1
                  from work_node n
                  join work_node_evidence e on e.work_node_id = n.id
                  join message m on m.id = e.message_id
                  where n.job_id = j.id
                    and m.conversation_id = ?
              )
            order by j.last_activity_at desc
            limit ?
            """
                    .formatted(JOB_COLUMNS_ALIASED, ENDED_STATES);

    @Override
    public List<Job> openJobsTouchedIn(UUID conversationId, int limit) {
        return jdbc.query(OPEN_JOBS_TOUCHED_IN, (row, index) -> job(row), conversationId, limit);
    }

    private static final String JOBS_TOUCHED_IN =
            """
            select %s
            from job j
            where exists (
                  select 1
                  from work_node n
                  join work_node_evidence e on e.work_node_id = n.id
                  join message m on m.id = e.message_id
                  where n.job_id = j.id
                    and m.conversation_id = ?
              )
            order by j.last_activity_at desc
            limit ?
            """
                    .formatted(JOB_COLUMNS_ALIASED);

    @Override
    public List<Job> jobsTouchedIn(UUID conversationId, int limit) {
        return jdbc.query(JOBS_TOUCHED_IN, (row, index) -> job(row), conversationId, limit);
    }

    private static final String OPEN_JOB_FOR_COUNTERPARTY_SINCE =
            """
            select %s
            from job
            where counterparty_id = ?
              and opened_at >= ?
              and status not in %s
            order by opened_at desc
            limit 1
            """
                    .formatted(JOB_COLUMNS, ENDED_STATES);

    @Override
    public Optional<Job> openJobForCounterpartySince(UUID counterpartyId, Instant since) {
        if (counterpartyId == null) {
            return Optional.empty();
        }

        return jdbc
                .query(OPEN_JOB_FOR_COUNTERPARTY_SINCE, (row, index) -> job(row), counterpartyId, Timestamp.from(since))
                .stream()
                .findFirst();
    }

    private static final String RECENTLY_TOUCHED_OPEN_JOBS =
            """
            select %s
            from job
            where status not in %s
            order by last_activity_at desc
            limit ?
            """
                    .formatted(JOB_COLUMNS, ENDED_STATES);

    @Override
    public List<Job> recentlyTouchedOpenJobs(int limit) {
        return jdbc.query(RECENTLY_TOUCHED_OPEN_JOBS, (row, index) -> job(row), limit);
    }

    private static final String RECORD_EVIDENCE =
            """
            insert into work_node_evidence (id, work_node_id, message_id, added_at, origin)
            values (?, ?, ?, ?, ?)
            """;

    private static final String SPLIT_EVERY_MARK_ON_A_FANNED_OUT_MESSAGE =
            """
            update work_node_evidence set origin = 'SPLIT'
            where message_id = ?
              and origin = 'PRIMARY'
              and (
                  select count(*) from work_node_evidence other
                  where other.message_id = ? and other.origin in ('PRIMARY', 'SPLIT')
              ) > 1
            """;

    @Override
    public void recordEvidence(WorkNode node, UUID messageId, EvidenceOrigin origin) {
        jdbc.update(
                RECORD_EVIDENCE,
                UUID.randomUUID(),
                node.id().value(),
                messageId,
                Timestamp.from(Instant.now(clock)),
                origin.name());

        jdbc.update(SPLIT_EVERY_MARK_ON_A_FANNED_OUT_MESSAGE, messageId, messageId);
    }

    @Override
    public Optional<WorkNode> findMarkOf(UUID messageId, JobId job, UUID performerId, NodeKind kind) {
        return jdbc
                .query(FIND_MARK_OF, (row, index) -> node(row), messageId, job.value(), performerId, kind.name())
                .stream()
                .findFirst();
    }

    private Job job(ResultSet row) throws SQLException {
        UUID reworkOf = row.getObject("rework_of_job_id", UUID.class);

        Job job = Job.rehydrated(
                JobId.of(row.getObject("id", UUID.class)),
                row.getString("name"),
                row.getObject("opened_by", UUID.class),
                instant(row.getTimestamp("opened_at")),
                Job.Status.valueOf(row.getString("status")),
                row.getBoolean("standing"),
                instant(row.getTimestamp("closed_at")),
                instant(row.getTimestamp("last_activity_at")),
                row.getObject("counterparty_id", UUID.class),
                reworkOf == null ? null : JobId.of(reworkOf));

        job.restoreClosure(row.getString("close_reason"), row.getBoolean("shape_eligible"));

        job.restoreProject(row.getString("project_label"));

        return job;
    }

    private Track track(ResultSet row) throws SQLException {
        TrackKey key = new TrackKey(
                row.getObject("from_role_id", UUID.class),
                row.getObject("to_role_id", UUID.class),
                row.getObject("performer_id", UUID.class),
                KeyBasis.valueOf(row.getString("key_basis")));

        return Track.rehydrated(
                TrackId.of(row.getObject("id", UUID.class)),
                JobId.of(row.getObject("job_id", UUID.class)),
                key,
                instant(row.getTimestamp("opened_at")),
                TrackState.valueOf(row.getString("state")),
                instant(row.getTimestamp("closed_at")),
                instant(row.getTimestamp("last_activity_at")),
                enumOrNull(CloseReason.class, row.getString("close_reason")),
                enumOrNull(Completeness.class, row.getString("completeness")),
                row.getBoolean("disrupted"),
                trackId(row.getObject("continues_track_id", UUID.class)),
                row.getObject("track_type_id", UUID.class),
                row.getObject("process_template_id", UUID.class),
                row.getObject("performer_id", UUID.class));
    }

    private WorkNode node(ResultSet row) throws SQLException {
        return WorkNode.rehydrated(
                WorkNodeId.of(row.getObject("id", UUID.class)),
                JobId.of(row.getObject("job_id", UUID.class)),
                row.getString("text"),
                row.getObject("creator_id", UUID.class),
                row.getObject("creator_role_id", UUID.class),
                NodeKind.valueOf(row.getString("kind")),
                instant(row.getTimestamp("created_at")),
                WorkNodeState.valueOf(row.getString("state")),
                Direction.valueOf(row.getString("direction")),
                trackId(row.getObject("track_id", UUID.class)),
                enumOrNull(SubjectSource.class, row.getString("subject_source")),
                row.getObject("performer_id", UUID.class),
                row.getObject("performer_role_id", UUID.class),
                enumOrNull(OutputType.class, row.getString("output_type")),
                instant(row.getTimestamp("started_at")),
                instant(row.getTimestamp("closed_at")),
                instant(row.getTimestamp("nudged_at")),
                fingerprint(row),
                row.getBigDecimal("weight"),
                row.getObject("task_template_id", UUID.class),
                row.getString("work_type"),
                row.getString("title"),
                row.getString("detail"),
                readChecklist(row.getString("checklist")),
                row.getObject("enriched_by", UUID.class));
    }

    private static Fingerprint fingerprint(ResultSet row) throws SQLException {
        if (row.getString("fingerprint") == null) {
            return null;
        }
        Object median = row.getObject("fingerprint_median_work_ms");
        return Fingerprint.rehydrated(
                row.getObject("fingerprint_performer_role_id", UUID.class),
                median == null ? null : Duration.ofMillis(((Number) median).longValue()),
                enumOrNull(OutputType.class, row.getString("fingerprint_output_type")),
                row.getObject("fingerprint_preceding_role_id", UUID.class),
                enumOrNull(Direction.class, row.getString("fingerprint_preceding_direction")),
                row.getObject("fingerprint_following_role_id", UUID.class),
                row.getInt("fingerprint_position_in_track"));
    }

    private static TrackId trackId(UUID value) {
        return value == null ? null : TrackId.of(value);
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
