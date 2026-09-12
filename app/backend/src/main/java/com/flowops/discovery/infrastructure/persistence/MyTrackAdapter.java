package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.MyTrackPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MyTrackAdapter implements MyTrackPort {
    private final JdbcTemplate jdbc;

    public MyTrackAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<TrackLine> myTrack(UUID viewer) {
        List<Row> rows = jdbc.query(
                """
                select
                    b.id              as bracket_id,
                    b.conversation_id as conversation_id,
                    b.project_label   as project,
                    b.work_type       as work_type,
                    b.state           as state,
                    b.close_kind      as close_kind,
                    n.id              as node_id,
                    n.node_role       as node_role,
                    ev.message_id     as message_id
                from work_bracket b
                join work_node n on n.bracket_id = b.id
                left join lateral (
                    select e.message_id
                    from work_node_evidence e
                    where e.work_node_id = n.id
                    order by e.added_at, e.id
                    limit 1
                ) ev on true
                where b.performer_ref = ?
                  and b.is_boundary = false
                order by (b.state in ('OPEN','WAITING')) desc, b.last_activity_at desc, b.id,
                         n.created_at, n.id
                """,
                (rs, index) -> new Row(
                        rs.getObject("bracket_id", UUID.class),
                        rs.getObject("conversation_id", UUID.class),
                        address(rs.getString("project"), rs.getString("work_type")),
                        rs.getString("work_type"),
                        rs.getString("state"),
                        rs.getString("close_kind"),
                        rs.getObject("node_id", UUID.class),
                        rs.getString("node_role"),
                        rs.getObject("message_id", UUID.class)),
                viewer);

        Map<UUID, List<TrackNode>> chains = new LinkedHashMap<>();
        Map<UUID, Row> lines = new LinkedHashMap<>();

        for (Row row : rows) {
            lines.putIfAbsent(row.bracketId(), row);
            chains.computeIfAbsent(row.bracketId(), id -> new ArrayList<>())
                    .add(new TrackNode(row.nodeId(), row.nodeRole(), row.messageId()));
        }

        List<TrackLine> track = new ArrayList<>();
        for (Row line : lines.values()) {
            track.add(new TrackLine(
                    line.bracketId(),
                    line.conversationId(),
                    line.address(),
                    line.workType(),
                    line.state(),
                    line.closeKind(),
                    List.copyOf(chains.get(line.bracketId()))));
        }

        return List.copyOf(track);
    }

    private record Row(
            UUID bracketId,
            UUID conversationId,
            String address,
            String workType,
            String state,
            String closeKind,
            UUID nodeId,
            String nodeRole,
            UUID messageId) {}

    private static String address(String project, String workType) {
        return project == null ? workType : project + " › " + workType;
    }
}
