package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.TrackerRailPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TrackerRailAdapter implements TrackerRailPort {
    private final JdbcTemplate jdbc;

    public TrackerRailAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Lane> openWork() {
        List<Row> rows = jdbc.query(
                """
                select
                    j.id            as job_id,
                    c.name          as client,
                    j.project_label as project,
                    j.name          as job_name,
                    b.id            as bracket_id,
                    b.work_type     as work_type,
                    coalesce(u.display_name, u.email) as performer_name,
                    b.state         as state,
                    b.conversation_id as conversation_id,
                    e.message_id    as message_id
                from work_bracket b
                join job j on j.id = b.job_id
                left join counterparty c on c.id = j.counterparty_id
                left join auth_user u on u.id = b.performer_ref
                left join work_node_evidence e
                       on e.work_node_id = b.opened_by_node and e.origin in ('PRIMARY', 'SPLIT')
                where b.state in ('OPEN', 'WAITING')
                  and b.is_boundary = false
                order by j.last_activity_at desc, j.id, b.opened_at
                """,
                (rs, index) -> new Row(
                        rs.getObject("job_id", UUID.class),
                        rs.getString("client"),
                        rs.getString("project"),
                        rs.getString("job_name"),
                        new Mark(
                                rs.getObject("bracket_id", UUID.class),
                                rs.getObject("job_id", UUID.class),
                                rs.getString("work_type"),
                                rs.getString("performer_name"),
                                rs.getString("state"),
                                rs.getObject("conversation_id", UUID.class),
                                rs.getObject("message_id", UUID.class))));

        Map<UUID, Lane> lanes = new LinkedHashMap<>();

        for (Row row : rows) {
            Lane lane = lanes.computeIfAbsent(
                    row.jobId(), id -> new Lane(id, row.client(), row.project(), row.jobName(), new ArrayList<>()));

            lane.marks().add(row.mark());
        }

        return List.copyOf(lanes.values());
    }

    private record Row(UUID jobId, String client, String project, String jobName, Mark mark) {}
}
