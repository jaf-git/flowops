package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.domain.enums.BracketState;
import com.flowops.discovery.domain.enums.CloseKind;
import com.flowops.discovery.domain.enums.OutputKind;
import com.flowops.discovery.domain.enums.WaitKind;
import com.flowops.discovery.domain.model.BracketAddress;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.WorkBracket;
import com.flowops.discovery.domain.model.WorkNodeId;
import com.flowops.discovery.domain.model.WorkNodeWait;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkBracketAdapter implements WorkBracketPort {
    private final JdbcTemplate jdbc;

    public WorkBracketAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public BracketId nextBracketId() {
        return BracketId.fresh();
    }

    @Override
    public UUID nextWaitId() {
        return UUID.randomUUID();
    }

    @Override
    public void save(WorkBracket bracket) {
        jdbc.update(
                """
                insert into work_bracket (
                    id, job_id, conversation_id, counterparty_id, project_label, work_type, performer_ref,
                    opened_by_node, closed_by_node, closure_right, state, close_kind, output_kind, output_value,
                    parent_bracket_id, depth, continues_bracket_id, disrupted, is_boundary, work_type_overridden,
                    nudged_at, answered_nudge, opened_at, closed_at, last_activity_at, closure_stands_in_for)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                on conflict (id) do update set

                    performer_ref    = excluded.performer_ref,
                    closed_by_node   = excluded.closed_by_node,
                    closure_right    = excluded.closure_right,
                    closure_stands_in_for = excluded.closure_stands_in_for,
                    state            = excluded.state,
                    close_kind       = excluded.close_kind,
                    output_kind      = excluded.output_kind,
                    output_value     = excluded.output_value,
                    disrupted        = work_bracket.disrupted or excluded.disrupted,

                    work_type_overridden = work_bracket.work_type_overridden or excluded.work_type_overridden,
                    nudged_at        = coalesce(work_bracket.nudged_at, excluded.nudged_at),
                    answered_nudge   = work_bracket.answered_nudge or excluded.answered_nudge,
                    closed_at        = excluded.closed_at,
                    last_activity_at = excluded.last_activity_at
                """,
                bracket.id().value(),
                bracket.jobId().value(),
                bracket.address().conversationId(),
                bracket.address().counterpartyId(),
                bracket.address().projectLabel(),
                bracket.address().workType(),
                bracket.address().performerId(),
                bracket.openedByNode().value(),
                bracket.closedByNode().map(WorkNodeId::value).orElse(null),
                bracket.closureRight(),
                bracket.state().name(),
                bracket.closeKind().map(Enum::name).orElse(null),
                bracket.outputKind().map(Enum::name).orElse(null),
                bracket.outputValue().orElse(null),
                bracket.parentBracketId().map(BracketId::value).orElse(null),
                bracket.depth(),
                bracket.continuesBracketId().map(BracketId::value).orElse(null),
                bracket.isDisrupted(),
                bracket.isBoundary(),
                bracket.isWorkTypeOverridden(),
                bracket.nudgedAt().map(Timestamp::from).orElse(null),
                bracket.answeredNudge(),
                Timestamp.from(bracket.openedAt()),
                bracket.closedAt().map(Timestamp::from).orElse(null),
                Timestamp.from(bracket.lastActivityAt()),
                bracket.closureStandsInFor().orElse(null));
    }

    @Override
    public Optional<WorkBracket> find(BracketId id) {
        return jdbc
                .query("select * from work_bracket where id = ?", WorkBracketAdapter::readBracket, id.value())
                .stream()
                .findFirst();
    }

    @Override
    public Optional<WorkBracket> findOpenAt(JobId job, BracketAddress address) {
        return jdbc
                .query(
                        """
                        select * from work_bracket
                        where job_id = ?
                          and conversation_id = ?
                          and counterparty_id is not distinct from ?
                          and project_label   is not distinct from ?
                          and work_type = ?
                          and performer_ref   is not distinct from ?
                          and state in ('OPEN', 'WAITING')
                          and is_boundary = false
                        """,
                        WorkBracketAdapter::readBracket,
                        job.value(),
                        address.conversationId(),
                        address.counterpartyId(),
                        address.projectLabel(),
                        address.workType(),
                        address.performerId())
                .stream()
                .findFirst();
    }

    @Override
    public List<WorkBracket> findLiveIn(JobId job) {
        return jdbc.query(
                "select * from work_bracket where job_id = ? and state in ('OPEN','WAITING') order by opened_at",
                WorkBracketAdapter::readBracket,
                job.value());
    }

    @Override
    public List<WorkBracket> liveWorkIn(JobId job) {
        return jdbc.query(
                """
                select * from work_bracket
                where job_id = ? and state in ('OPEN','WAITING') and is_boundary = false
                order by opened_at
                """,
                WorkBracketAdapter::readBracket,
                job.value());
    }

    @Override
    public void withdrawBracketsOf(JobId job) {
        jdbc.update(
                "update work_node set bracket_id = null, node_role = null "
                        + "where bracket_id in (select id from work_bracket where job_id = ?)",
                job.value());

        jdbc.update(
                "delete from work_node_wait where bracket_id in (select id from work_bracket where job_id = ?)",
                job.value());

        jdbc.update("delete from work_bracket where job_id = ?", job.value());
    }

    @Override
    public void mergeNodesInto(BracketId source, BracketId target) {
        jdbc.update(
                """
                update work_node
                set bracket_id = ?,
                    node_role  = case when node_role = 'START' then 'WORK' else node_role end
                where bracket_id = ?
                """,
                target.value(),
                source.value());
    }

    @Override
    public List<WorkNodeWait> waitsPastTheirExpectedDate(Instant now) {
        return jdbc.query(
                """
                select w.* from work_node_wait w
                join work_bracket b on b.id = w.bracket_id
                where w.satisfied_at is null
                  and w.cancelled_at is null
                  and w.expected_by is not null
                  and w.expected_by < ?
                  and b.state in ('OPEN', 'WAITING')
                order by w.expected_by
                """,
                WorkBracketAdapter::readWait,
                Timestamp.from(now));
    }

    @Override
    public List<WorkNodeWait> externalWaitsOpenSince(Instant before) {
        return jdbc.query(
                """
                select w.* from work_node_wait w
                join work_bracket b on b.id = w.bracket_id
                where w.satisfied_at is null
                  and w.cancelled_at is null
                  and w.kind in ('CLIENT', 'SUPPLIER')
                  and w.opened_at < ?
                  and b.state in ('OPEN', 'WAITING')
                order by w.opened_at
                """,
                WorkBracketAdapter::readWait,
                Timestamp.from(before));
    }

    @Override
    public List<WorkBracket> liveWorkEverywhere() {
        return jdbc.query(
                "select * from work_bracket where state in ('OPEN','WAITING') order by opened_at",
                WorkBracketAdapter::readBracket);
    }

    @Override
    public void publishArtifact(WorkBracket delivered, UUID counterparty) {
        jdbc.update(
                """
                insert into client_artifact (id, counterparty_id, job_id, bracket_id, kind, value,
                                             conversation_id, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (bracket_id) do nothing
                """,
                UUID.randomUUID(),
                counterparty,
                delivered.jobId().value(),
                delivered.id().value(),
                delivered.outputKind().map(Enum::name).orElseThrow(),
                delivered.outputValue().orElseThrow(),
                delivered.address().conversationId(),
                Timestamp.from(delivered.closedAt().orElseThrow()));
    }

    @Override
    public Optional<WorkBracket> boundaryOf(JobId job) {
        return jdbc
                .query(
                        "select * from work_bracket where job_id = ? and is_boundary = true",
                        WorkBracketAdapter::readBracket,
                        job.value())
                .stream()
                .findFirst();
    }

    @Override
    public List<WorkBracket> continuationChainFrom(BracketId head) {
        return jdbc.query(
                """
                with recursive chain as (
                    select b.* from work_bracket b where b.id = ?
                    union all
                    select nxt.* from work_bracket nxt join chain c on nxt.continues_bracket_id = c.id
                )
                select * from chain
                """,
                WorkBracketAdapter::readBracket,
                head.value());
    }

    @Override
    public void save(WorkNodeWait wait) {
        jdbc.update(
                """
                insert into work_node_wait (
                    id, bracket_id, kind, on_bracket_id, reason, expected_by, opened_at, satisfied_at, cancelled_at)
                values (?,?,?,?,?,?,?,?,?)
                on conflict (id) do update set
                    satisfied_at = excluded.satisfied_at,
                    cancelled_at = excluded.cancelled_at
                """,
                wait.id(),
                wait.bracketId().value(),
                wait.kind().name(),
                wait.onBracketId().map(BracketId::value).orElse(null),
                wait.reason().orElse(null),
                wait.expectedBy().map(Timestamp::from).orElse(null),
                Timestamp.from(wait.openedAt()),
                wait.satisfiedAt().map(Timestamp::from).orElse(null),
                wait.cancelledAt().map(Timestamp::from).orElse(null));
    }

    @Override
    public java.util.Optional<WorkNodeWait> findWait(UUID id) {
        return jdbc.query("select * from work_node_wait where id = ?", WorkBracketAdapter::readWait, id).stream()
                .findFirst();
    }

    @Override
    public List<WorkNodeWait> openWaitsHeldBy(BracketId bracket) {
        return jdbc.query(
                "select * from work_node_wait where bracket_id = ? and satisfied_at is null and cancelled_at is null",
                WorkBracketAdapter::readWait,
                bracket.value());
    }

    @Override
    public List<WorkNodeWait> openWaitsOn(BracketId bracket) {
        return jdbc.query(
                "select * from work_node_wait where on_bracket_id = ? and satisfied_at is null and cancelled_at is null",
                WorkBracketAdapter::readWait,
                bracket.value());
    }

    @Override
    public List<WorkBracket> awaitingTheirOneNudge(Instant idleSince) {
        return jdbc.query(
                """
                select * from work_bracket
                where state in ('OPEN','WAITING')
                  and nudged_at is null
                  and is_boundary = false
                  and last_activity_at < ?
                """,
                WorkBracketAdapter::readBracket,
                Timestamp.from(idleSince));
    }

    @Override
    public List<WorkBracket> readyToLapse(Instant nudgedBefore) {
        return jdbc.query(
                """
                select b.* from work_bracket b
                where b.state in ('OPEN','WAITING')
                  and b.nudged_at is not null
                  and b.answered_nudge = false
                  and b.is_boundary = false
                  and b.nudged_at < ?
                  and not exists (
                        select 1 from work_node marked
                        where marked.marker_id = b.closure_right
                          and marked.created_at > b.nudged_at)
                  and not exists (
                        select 1 from work_bracket ended
                        where ended.closure_right = b.closure_right
                          and ended.closed_at > b.nudged_at)
                  and not exists (
                        select 1 from work_node_wait w
                        join work_bracket held on held.id = w.bracket_id
                        where held.closure_right = b.closure_right
                          and (w.opened_at > b.nudged_at
                               or w.satisfied_at > b.nudged_at
                               or w.cancelled_at > b.nudged_at))
                  and not exists (
                        select 1 from message m
                        where m.author_id = b.closure_right
                          and m.sent_at > b.nudged_at
                          and m.deleted_at is null)
                """,
                WorkBracketAdapter::readBracket,
                Timestamp.from(nudgedBefore));
    }

    @Override
    public Optional<WorkNodeId> rootNodeOf(JobId job) {
        return jdbc
                .query(
                        "select id from work_node where job_id = ? order by created_at limit 1",
                        (rs, row) -> new WorkNodeId(rs.getObject("id", UUID.class)),
                        job.value())
                .stream()
                .findFirst();
    }

    @Override
    public List<WorkNodeId> chainOf(JobId job, UUID performer) {
        return jdbc.query(
                "select id from work_node where job_id = ? and performer_id = ? order by created_at desc",
                (rs, row) -> new WorkNodeId(rs.getObject("id", UUID.class)),
                job.value(),
                performer);
    }

    @Override
    public BracketId mainLevelAncestorOf(BracketId bracket) {
        return jdbc
                .query(
                        """
                        select case when b.depth = 0 then b.id else coalesce(p.id, b.id) end as ancestor
                        from work_bracket b
                        left join work_bracket p on p.id = b.parent_bracket_id
                        where b.id = ?
                        """,
                        (rs, row) -> BracketId.of(rs.getObject("ancestor", UUID.class)),
                        bracket.value())
                .stream()
                .findFirst()
                .orElse(bracket);
    }

    @Override
    public boolean placeNode(
            WorkNodeId node,
            BracketId bracket,
            com.flowops.discovery.domain.enums.NodeRole role,
            BracketAddress address,
            UUID marker,
            WorkNodeId parent) {
        return 1
                == jdbc.update(
                        """
                update work_node set
                    bracket_id      = ?,
                    node_role       = ?,
                    conversation_id = ?,
                    work_type       = ?,
                    marker_id       = ?,
                    parent_node_id  = coalesce(?, parent_node_id),
                    evidence_origin = coalesce(evidence_origin, 'PRIMARY')
                where id = ?
                  and (bracket_id is null or bracket_id = ?)
                """,
                        bracket.value(),
                        role.name(),
                        address.conversationId(),
                        address.workType(),
                        marker,
                        parent == null ? null : parent.value(),
                        node.value(),
                        bracket.value());
    }

    @Override
    public WorkNodeId appendStartNode(
            WorkBracket successor, WorkNodeId announcement, WorkNodeId parent, UUID marker, Instant at) {
        UUID startNode = UUID.randomUUID();

        jdbc.update(
                """
                insert into work_node (
                    id, job_id, track_id, text, detail, creator_id, creator_role_id,
                    performer_id, performer_role_id, created_at, state, direction, kind,
                    bracket_id, node_role, conversation_id, work_type, marker_id, evidence_origin,
                    parent_node_id)
                select
                    ?, o.job_id, o.track_id, o.text, o.detail, o.creator_id, o.creator_role_id,
                    ?, o.performer_role_id, ?, 'SELF', 'STANDALONE', 'WORK',
                    ?, 'START', ?, ?, ?, 'ADDITIONAL', ?
                from work_node o
                where o.id = ?
                """,
                startNode,
                successor.address().performerId(),
                Timestamp.from(at),
                successor.id().value(),
                successor.address().conversationId(),
                successor.address().workType(),
                marker,
                parent == null ? null : parent.value(),
                announcement.value());

        jdbc.update(
                """
                insert into work_node_evidence (id, work_node_id, message_id, added_at, origin)
                select ?, ?, e.message_id, ?, 'ADDITIONAL'
                from work_node_evidence e
                where e.work_node_id = ?
                order by e.added_at, e.id
                limit 1
                """,
                UUID.randomUUID(),
                startNode,
                Timestamp.from(at),
                announcement.value());

        jdbc.update(
                "update work_bracket set opened_by_node = ? where id = ?",
                startNode,
                successor.id().value());

        return WorkNodeId.of(startNode);
    }

    @Override
    public WorkNodeId appendEndNode(WorkBracket bracket, UUID marker, Instant at) {
        UUID endNode = UUID.randomUUID();

        UUID lastNode = jdbc
                .query(
                        "select id from work_node where bracket_id = ? order by created_at desc, id desc limit 1",
                        (rs, row) -> rs.getObject("id", UUID.class),
                        bracket.id().value())
                .stream()
                .findFirst()
                .orElse(bracket.openedByNode().value());

        boolean oneMessageWork = Integer.valueOf(1)
                .equals(jdbc.queryForObject(
                        "select count(*) from work_node where bracket_id = ?",
                        Integer.class,
                        bracket.id().value()));

        String evidenceOrigin = oneMessageWork ? "SELF_CLOSE" : "PRIMARY";

        jdbc.update(
                """
                insert into work_node (
                    id, job_id, track_id, text, detail, creator_id, creator_role_id,
                    performer_id, performer_role_id, created_at, closed_at, state, direction, kind,
                    bracket_id, node_role, conversation_id, work_type, marker_id, evidence_origin,
                    parent_node_id, activity_id)
                select
                    ?, o.job_id, o.track_id, o.text, o.detail, o.creator_id, o.creator_role_id,
                    o.performer_id, o.performer_role_id, ?, ?, 'COMPLETED', 'COMPLETION', ?,
                    ?, 'END', ?, ?, ?, ?, ?, o.activity_id
                from work_node o
                where o.id = ?
                """,
                endNode,
                Timestamp.from(at),
                Timestamp.from(at),
                bracket.isBoundary() ? "JOB_END" : "WORK",
                bracket.id().value(),
                bracket.address().conversationId(),
                bracket.address().workType(),
                marker,
                evidenceOrigin,
                lastNode,
                lastNode);

        jdbc.update(
                "update work_node set paired_node_id = ? where id = ?",
                endNode,
                bracket.openedByNode().value());
        jdbc.update(
                "update work_node set paired_node_id = ? where id = ?",
                bracket.openedByNode().value(),
                endNode);

        jdbc.update(
                """
                insert into work_node_evidence (id, work_node_id, message_id, added_at, origin)
                select ?, ?, e.message_id, ?, 'ADDITIONAL'
                from work_node_evidence e
                where e.work_node_id = ? and e.origin in ('PRIMARY', 'SPLIT')
                order by e.added_at, e.id
                limit 1
                """,
                UUID.randomUUID(),
                endNode,
                Timestamp.from(at),
                lastNode);

        return WorkNodeId.of(endNode);
    }

    private static WorkBracket readBracket(ResultSet rs, int row) throws SQLException {
        BracketAddress address = new BracketAddress(
                rs.getObject("conversation_id", UUID.class),
                rs.getObject("counterparty_id", UUID.class),
                rs.getString("project_label"),
                rs.getString("work_type"),
                rs.getObject("performer_ref", UUID.class));

        WorkBracket bracket = WorkBracket.rehydrated(
                BracketId.of(rs.getObject("id", UUID.class)),
                JobId.of(rs.getObject("job_id", UUID.class)),
                address,
                new WorkNodeId(rs.getObject("opened_by_node", UUID.class)),
                rs.getObject("closure_right", UUID.class),
                BracketState.valueOf(rs.getString("state")),
                enumOrNull(rs.getString("close_kind"), CloseKind.class),
                enumOrNull(rs.getString("output_kind"), OutputKind.class),
                rs.getString("output_value"),
                nodeOrNull(rs.getObject("closed_by_node", UUID.class)),
                bracketOrNull(rs.getObject("parent_bracket_id", UUID.class)),
                bracketOrNull(rs.getObject("continues_bracket_id", UUID.class)),
                rs.getInt("depth"),
                rs.getBoolean("is_boundary"),
                rs.getBoolean("disrupted"),
                instantOrNull(rs.getTimestamp("nudged_at")),
                rs.getBoolean("answered_nudge"),
                rs.getTimestamp("opened_at").toInstant(),
                instantOrNull(rs.getTimestamp("closed_at")),
                rs.getTimestamp("last_activity_at").toInstant());

        bracket.restoreStandIn(rs.getObject("closure_stands_in_for", UUID.class));

        bracket.restoreWorkTypeOverridden(rs.getBoolean("work_type_overridden"));

        return bracket;
    }

    private static WorkNodeWait readWait(ResultSet rs, int row) throws SQLException {
        return WorkNodeWait.rehydrated(
                rs.getObject("id", UUID.class),
                BracketId.of(rs.getObject("bracket_id", UUID.class)),
                WaitKind.valueOf(rs.getString("kind")),
                bracketOrNull(rs.getObject("on_bracket_id", UUID.class)),
                rs.getString("reason"),
                instantOrNull(rs.getTimestamp("expected_by")),
                rs.getTimestamp("opened_at").toInstant(),
                instantOrNull(rs.getTimestamp("satisfied_at")),
                instantOrNull(rs.getTimestamp("cancelled_at")));
    }

    private static <E extends Enum<E>> E enumOrNull(String value, Class<E> type) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private static BracketId bracketOrNull(UUID value) {
        return value == null ? null : BracketId.of(value);
    }

    private static WorkNodeId nodeOrNull(UUID value) {
        return value == null ? null : new WorkNodeId(value);
    }

    private static Instant instantOrNull(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
