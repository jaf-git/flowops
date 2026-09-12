package com.flowops.discovery.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowops.discovery.application.shared.port.PublicGraphPort;
import com.flowops.discovery.domain.model.JobId;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class PublicGraphAdapter implements PublicGraphPort {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    private final ObjectMapper json;

    public PublicGraphAdapter(JdbcTemplate jdbc, Clock clock, ObjectMapper json) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.json = json;
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

    @Override
    public List<PublicNode> shapeOf(JobId job) {
        return jdbc.query(
                """
                select
                    n.id                as node_id,
                    b.id                as bracket_id,
                    b.work_type         as work_type,

                    (b.performer_ref is null) as unclaimed,
                    case
                        when b.performer_ref is null then null
                        else coalesce(u.display_name, u.email, 'Former member')
                    end                 as performer_name,
                    b.state             as state,
                    extract(epoch from (coalesce(b.closed_at, ?) - b.opened_at))::bigint as elapsed,
                    case
                        when b.state = 'WAITING' then 'waiting'
                        when b.state in ('CLOSED','LAPSED') then coalesce(b.close_kind, 'closed')
                        else 'working'
                    end                 as phase,
                    b.close_kind        as close_kind,
                    n.node_role         as node_role,
                    b.is_boundary       as boundary,

                    ev.message_id       as message_id,
                    ev.conversation_id  as conversation_id,

                    n.text              as text,
                    n.title             as title,
                    n.detail            as detail,
                    n.checklist         as checklist,
                    n.direction         as direction,
                    n.kind              as kind,
                    n.output_type       as output_type,
                    n.task_template_id  as task_template_id,
                    a.name              as activity,

                    mk.display_name     as marker_name,

                    d.name              as department,
                    b.work_type_overridden as work_type_overridden,
                    cp.name             as client,
                    b.project_label     as project_label
                from work_node n
                join work_bracket b on b.id = n.bracket_id
                left join auth_user u on u.id = b.performer_ref
                left join auth_user mk on mk.id = n.marker_id
                left join counterparty cp on cp.id = b.counterparty_id
                left join activity a on a.id = n.activity_id
                left join workspace_membership wm on wm.user_id = b.performer_ref
                left join functional_role fr on fr.id = wm.functional_role_id
                left join department d on d.id = fr.department_id
                left join lateral (
                    select e.message_id, m.conversation_id
                    from work_node_evidence e
                    join message m on m.id = e.message_id
                    where e.work_node_id = n.id
                    order by e.added_at, e.id
                    limit 1
                ) ev on true
                where n.job_id = ?
                order by n.created_at
                """,
                (rs, row) -> new PublicNode(
                        rs.getObject("node_id", UUID.class),
                        rs.getObject("bracket_id", UUID.class),
                        rs.getString("work_type"),
                        rs.getBoolean("work_type_overridden"),
                        rs.getString("department"),
                        rs.getString("performer_name"),
                        rs.getBoolean("unclaimed"),
                        rs.getString("marker_name"),
                        rs.getString("client"),
                        rs.getString("project_label"),
                        rs.getString("state"),
                        rs.getLong("elapsed"),
                        rs.getString("phase"),
                        rs.getString("close_kind"),
                        rs.getString("node_role"),
                        rs.getBoolean("boundary"),
                        rs.getString("text"),
                        rs.getString("title"),
                        rs.getString("detail"),
                        readChecklist(rs.getString("checklist")),
                        rs.getString("direction"),
                        rs.getString("kind"),
                        rs.getString("output_type"),
                        rs.getObject("task_template_id", UUID.class),
                        rs.getString("activity"),
                        rs.getObject("message_id", UUID.class),
                        rs.getObject("conversation_id", UUID.class)),
                Timestamp.from(clock.instant()),
                job.value());
    }

    @Override
    public List<PublicEdge> edgesOf(JobId job) {
        List<PublicEdge> edges = new ArrayList<>();

        edges.addAll(parentage(job));
        edges.addAll(waits(job));
        edges.addAll(successions(job));

        Map<String, PublicEdge> byEnds = new LinkedHashMap<>();

        for (PublicEdge edge : edges) {
            if (edge.fromNodeId() == null
                    || edge.toNodeId() == null
                    || edge.fromNodeId().equals(edge.toNodeId())) {
                continue;
            }

            String ends = edge.kind() + " " + edge.fromNodeId() + " " + edge.toNodeId();
            PublicEdge kept = byEnds.get(ends);

            if (kept == null
                    || (kept.state() != PublicEdge.EdgeState.LIVE && edge.state() == PublicEdge.EdgeState.LIVE)) {
                byEnds.put(ends, edge);
            }
        }

        return List.copyOf(byEnds.values());
    }

    private List<PublicEdge> parentage(JobId job) {
        return jdbc.query(
                """
                select child.parent_node_id as from_node, child.id as to_node
                from work_node child
                join work_node parent on parent.id = child.parent_node_id
                where child.job_id = ?
                  and parent.job_id = ?
                  and child.parent_node_id is not null
                order by child.created_at
                """,
                edge(PublicEdge.EdgeKind.PARENTAGE),
                job.value(),
                job.value());
    }

    private List<PublicEdge> waits(JobId job) {
        return jdbc.query(
                """
                select
                    coalesce(awaited.closed_by_node, awaited.opened_by_node) as from_node,
                    waiter.opened_by_node                                    as to_node,
                    case
                        when w.satisfied_at is not null then 'SATISFIED'
                        when w.cancelled_at is not null then 'WITHDRAWN'
                        else 'LIVE'
                    end                                                      as state
                from work_node_wait w
                join work_bracket waiter  on waiter.id  = w.bracket_id
                join work_bracket awaited on awaited.id = w.on_bracket_id
                where waiter.job_id = ?
                  and awaited.job_id = ?
                order by w.opened_at
                """,
                waitEdge(),
                job.value(),
                job.value());
    }

    private List<PublicEdge> successions(JobId job) {
        return jdbc.query(
                """
                select
                    coalesce(previous.closed_by_node, previous.opened_by_node) as from_node,
                    successor.opened_by_node                                   as to_node
                from work_bracket successor
                join work_bracket previous on previous.id = successor.continues_bracket_id
                where successor.job_id = ?
                  and previous.job_id = ?
                order by successor.opened_at
                """,
                edge(PublicEdge.EdgeKind.SUCCESSION),
                job.value(),
                job.value());
    }

    private static RowMapper<PublicEdge> edge(PublicEdge.EdgeKind kind) {
        return (rs, row) ->
                new PublicEdge(kind, rs.getObject("from_node", UUID.class), rs.getObject("to_node", UUID.class), null);
    }

    private static RowMapper<PublicEdge> waitEdge() {
        return (rs, row) -> new PublicEdge(
                PublicEdge.EdgeKind.WAIT,
                rs.getObject("from_node", UUID.class),
                rs.getObject("to_node", UUID.class),
                PublicEdge.EdgeState.valueOf(rs.getString("state")));
    }

    @Override
    public int matchesBeyondReach(String query, UUID caller) {
        Integer count = jdbc.queryForObject(
                """
                select count(*)
                from work_node n
                where n.text ilike '%' || ? || '%'
                  and n.conversation_id is not null
                  and n.conversation_id not in (
                      select cp.conversation_id from conversation_participant cp where cp.person_id = ?
                  )
                """,
                Integer.class, query, caller);

        return count == null ? 0 : count;
    }

    @Override
    public List<UUID> conversationsVisibleTo(UUID person) {
        return jdbc.queryForList(
                "select conversation_id from conversation_participant where person_id = ?", UUID.class, person);
    }
}
