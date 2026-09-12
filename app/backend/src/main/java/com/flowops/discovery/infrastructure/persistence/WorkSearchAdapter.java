package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.WorkSearchPort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkSearchAdapter implements WorkSearchPort {
    private final JdbcTemplate jdbc;

    public WorkSearchAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Match> readableMatches(String query, UUID caller, int limit) {
        return jdbc.query(
                """
                select
                    n.id              as node_id,
                    n.bracket_id      as bracket_id,
                    n.job_id          as job_id,
                    n.conversation_id as conversation_id,
                    n.work_type       as work_type,
                    n.text            as text
                from work_node n
                join conversation_participant cp
                  on cp.conversation_id = n.conversation_id
                 and cp.person_id = ?
                where n.text ilike '%' || ? || '%'
                order by n.created_at desc
                limit ?
                """,
                (rs, row) -> new Match(
                        rs.getObject("node_id", UUID.class),
                        rs.getObject("bracket_id", UUID.class),
                        rs.getObject("job_id", UUID.class),
                        rs.getObject("conversation_id", UUID.class),
                        rs.getString("work_type"),
                        rs.getString("text")),
                caller,
                query,
                limit);
    }
}
