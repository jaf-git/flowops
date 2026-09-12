package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.NodeTrailPort;
import com.flowops.discovery.domain.enums.WorkNodeState;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class NodeTrailAdapter implements NodeTrailPort {
    private final JdbcTemplate jdbc;

    public NodeTrailAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String TRAIL_OF =
            """
            select from_state, to_state, actor_user_id, reason, occurred_at
              from work_node_state_transition
             where node_id = ?
             order by occurred_at, seq
            """;

    @Override
    public List<RecordedMove> trailOf(WorkNodeId node) {
        return jdbc.query(TRAIL_OF, (row, index) -> move(row), node.value());
    }

    private static RecordedMove move(ResultSet row) throws SQLException {
        String from = row.getString("from_state");
        return new RecordedMove(
                Optional.ofNullable(from).map(WorkNodeState::valueOf),
                WorkNodeState.valueOf(row.getString("to_state")),
                Optional.ofNullable(row.getObject("actor_user_id", UUID.class)),
                Optional.ofNullable(row.getString("reason")),
                row.getTimestamp("occurred_at").toInstant());
    }
}
