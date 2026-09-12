package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.WorkStripPort;
import com.flowops.discovery.domain.model.JobId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkStripAdapter implements WorkStripPort {
    private final JdbcTemplate jdbc;

    public WorkStripAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String RANKED_BY_EVENT_ORDER =
            """
            order by
                (select max(m.seq)
                   from work_node n
                   join work_node_evidence e on e.work_node_id = n.id
                   join message m on m.id = e.message_id
                  where n.bracket_id = b.id and m.conversation_id = b.conversation_id) desc nulls last,
                (select max(m.seq)
                   from work_node n
                   join work_node_evidence e on e.work_node_id = n.id
                   join message m on m.id = e.message_id
                  where n.marker_id = b.performer_ref) desc nulls last,
                (select count(*) from work_bracket used
                  where used.performer_ref = b.performer_ref
                    and used.work_type = b.work_type) desc,
                b.id
            """;

    @Override
    public List<OpenWork> othersOpenHere(JobId job, UUID conversationId, UUID notThisPerson) {
        return jdbc.query(
                """
                select b.id as bracket_id, b.job_id as job_id, b.work_type as work_type,
                       b.project_label as project_label, b.performer_ref as performer_id,
                       coalesce(u.display_name, u.email) as performer_name
                from work_bracket b
                left join auth_user u on u.id = b.performer_ref
                where b.job_id = ?
                  and b.conversation_id = ?
                  and b.state in ('OPEN', 'WAITING')

                  and b.is_boundary = false

                  and b.performer_ref is distinct from ?
                """
                        + RANKED_BY_EVENT_ORDER,
                WorkStripAdapter::readOpenWork,
                job.value(),
                conversationId,
                notThisPerson);
    }

    @Override
    public java.util.Set<UUID> liveBracketsStartedBy(UUID messageId) {
        return java.util.Set.copyOf(jdbc.queryForList(
                """
                select distinct n.bracket_id
                from work_node n
                join work_node_evidence e on e.work_node_id = n.id
                join work_bracket b on b.id = n.bracket_id
                join message m on m.id = e.message_id
                where e.message_id = ?
                  and n.node_role = 'START'
                  and b.state in ('OPEN', 'WAITING')
                  and b.performer_ref is distinct from m.author_id
                """,
                UUID.class,
                messageId));
    }

    @Override
    public List<Closable> liveWorkIn(UUID conversationId) {
        return jdbc.query(
                """
                select b.id as bracket_id, b.job_id as job_id, b.work_type as work_type,
                       b.project_label as project_label, b.performer_ref as performer_id,
                       coalesce(performer.display_name, performer.email) as performer_name,
                       b.closure_right as closure_right,
                       coalesce(holder.display_name, holder.email) as holder_name,

                       coalesce(
                           (select array_agg(coalesce(w_holder.display_name, w_holder.email))
                              from work_node_wait w
                              join work_bracket blocked on blocked.id = w.bracket_id
                              join auth_user w_holder on w_holder.id = blocked.closure_right
                             where w.on_bracket_id = b.id
                               and w.satisfied_at is null and w.cancelled_at is null),
                           '{}') as waiting_names
                from work_bracket b
                left join auth_user performer on performer.id = b.performer_ref
                join auth_user holder on holder.id = b.closure_right
                where b.conversation_id = ?
                  and b.state in ('OPEN', 'WAITING')

                  and b.is_boundary = false
                """
                        + RANKED_BY_EVENT_ORDER,
                WorkStripAdapter::readClosable,
                conversationId);
    }

    private static OpenWork readOpenWork(ResultSet rs, int row) throws SQLException {
        return new OpenWork(
                rs.getObject("bracket_id", UUID.class),
                rs.getObject("job_id", UUID.class),
                rs.getString("work_type"),
                destination(rs),
                rs.getObject("performer_id", UUID.class),
                rs.getString("performer_name"));
    }

    private static Closable readClosable(ResultSet rs, int row) throws SQLException {
        java.sql.Array raw = rs.getArray("waiting_names");
        List<String> waiting = new ArrayList<>();
        if (raw != null) {
            for (String name : (String[]) raw.getArray()) {
                waiting.add(name);
            }
        }

        return new Closable(
                rs.getObject("bracket_id", UUID.class),
                rs.getObject("job_id", UUID.class),
                rs.getString("work_type"),
                destination(rs),
                rs.getObject("performer_id", UUID.class),
                rs.getString("performer_name"),
                rs.getObject("closure_right", UUID.class),
                rs.getString("holder_name"),
                waiting.size(),
                List.copyOf(waiting));
    }

    private static String destination(ResultSet rs) throws SQLException {
        String project = rs.getString("project_label");
        String workType = rs.getString("work_type");
        return project == null ? workType : project + " › " + workType;
    }
}
