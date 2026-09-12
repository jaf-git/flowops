package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.MyWaitsPort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MyWaitsAdapter implements MyWaitsPort {
    private final JdbcTemplate jdbc;

    public MyWaitsAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<WaitOnMe> waitsOnMe(UUID viewer) {
        return jdbc.query(
                """
                select
                    w.id                as wait_id,
                    w.kind              as kind,
                    awaited.id          as on_bracket_id,
                    awaited.project_label as on_project,
                    awaited.work_type   as on_work_type,
                    awaited.conversation_id as conversation_id,
                    blocked.id          as waiting_bracket_id,
                    blocked.project_label as waiting_project,
                    blocked.work_type   as waiting_work_type,
                    coalesce(u.display_name, u.email) as waiting_performer_name,
                    w.opened_at         as declared_at
                from work_node_wait w
                join work_bracket awaited on awaited.id = w.on_bracket_id
                join work_bracket blocked on blocked.id = w.bracket_id
                left join auth_user u on u.id = blocked.performer_ref
                where awaited.performer_ref = ?
                  and w.satisfied_at is null
                  and w.cancelled_at is null
                order by w.opened_at, w.id
                """,
                (rs, index) -> new WaitOnMe(
                        rs.getObject("wait_id", UUID.class),
                        rs.getString("kind"),
                        rs.getObject("on_bracket_id", UUID.class),
                        address(rs.getString("on_project"), rs.getString("on_work_type")),
                        rs.getObject("waiting_bracket_id", UUID.class),
                        address(rs.getString("waiting_project"), rs.getString("waiting_work_type")),
                        rs.getString("waiting_performer_name"),
                        rs.getObject("conversation_id", UUID.class),
                        rs.getTimestamp("declared_at").toInstant()),
                viewer);
    }

    private static String address(String project, String workType) {
        return project == null ? workType : project + " › " + workType;
    }
}
