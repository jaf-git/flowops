package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.MarkedMessagePort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MarkedMessageAdapter implements MarkedMessagePort {
    private final JdbcTemplate jdbc;

    public MarkedMessageAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Mark> marksIn(UUID conversationId) {
        return jdbc.query(
                """
                select
                    e.message_id                      as message_id,
                    n.id                              as node_id,
                    b.id                              as bracket_id,
                    coalesce(b.work_type, '')         as work_type,
                    b.project_label                   as project_label,
                    coalesce(b.state, 'UNPLACED')     as state,
                    b.close_kind                      as close_kind,
                    n.job_id                          as job_id,
                    j.name                            as job_name,
                    b.performer_ref                  as performer_id,
                    coalesce(u.display_name, u.email) as performer_name,

                    cp.name                           as client,
                    n.title                           as title,
                    a.name                            as activity,
                    coalesce(b.is_boundary, false)    as boundary
                from work_node_evidence e
                join work_node n on n.id = e.work_node_id
                join message m on m.id = e.message_id
                join job j on j.id = n.job_id
                left join work_bracket b on b.id = n.bracket_id
                left join auth_user u on u.id = b.performer_ref
                left join counterparty cp on cp.id = b.counterparty_id
                left join activity a on a.id = n.activity_id
                where m.conversation_id = ?
                  and m.deleted_at is null

                order by e.added_at, e.id
                """,
                (rs, row) -> new Mark(
                        rs.getObject("message_id", UUID.class),
                        rs.getObject("node_id", UUID.class),
                        rs.getObject("bracket_id", UUID.class),
                        rs.getString("work_type"),
                        address(rs.getString("project_label"), rs.getString("work_type")),
                        rs.getString("project_label"),
                        rs.getString("client"),
                        rs.getString("title"),
                        rs.getString("state"),
                        rs.getString("close_kind"),
                        rs.getObject("job_id", UUID.class),
                        rs.getString("job_name"),
                        rs.getObject("performer_id", UUID.class),
                        rs.getString("performer_name"),
                        rs.getBoolean("boundary"),
                        rs.getString("activity")),
                conversationId);
    }

    private static String address(String projectLabel, String workType) {
        if (workType == null || workType.isBlank()) {
            return projectLabel == null ? "" : projectLabel;
        }
        return projectLabel == null || projectLabel.isBlank() ? workType : projectLabel + " › " + workType;
    }
}
