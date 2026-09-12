package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.EdgeGraphPort;
import com.flowops.discovery.domain.enums.EdgeKind;
import com.flowops.discovery.domain.enums.LoopExit;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.Loop;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.WorkEdge;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class EdgeGraphAdapter implements EdgeGraphPort {
    private final JdbcTemplate jdbc;

    public EdgeGraphAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String EDGE_COLUMNS =
            """
            id, from_node, to_node, kind, weight, observation_count, consistency_ratio,
            first_seen, last_seen, verified_by_owner
            """;

    private static final String SAVE_EDGE =
            """
            insert into work_edge (id, from_node, to_node, kind, weight, observation_count,
                                   consistency_ratio, first_seen, last_seen, verified_by_owner)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (from_node, to_node, kind) do update set
                weight = excluded.weight,
                observation_count = excluded.observation_count,
                consistency_ratio = excluded.consistency_ratio,
                last_seen = excluded.last_seen,
                verified_by_owner = work_edge.verified_by_owner or excluded.verified_by_owner
            """;

    @Override
    public void save(WorkEdge edge) {
        jdbc.update(
                SAVE_EDGE,
                edge.id(),
                edge.fromNode().value(),
                edge.toNode().value(),
                edge.kind().name(),
                edge.weight(),
                edge.observationCount(),
                edge.consistencyRatio(),
                Timestamp.from(edge.firstSeen()),
                Timestamp.from(edge.lastSeen()),
                edge.verifiedByOwner());
    }

    private static final String EDGES_WITHIN_TRACK =
            """
            select %s
            from work_edge e
            join work_node f on f.id = e.from_node
            join work_node t on t.id = e.to_node
            where f.track_id = ?
              and t.track_id = f.track_id
            order by e.first_seen, e.id
            """
                    .formatted(
                            """
                            e.id, e.from_node, e.to_node, e.kind, e.weight, e.observation_count,
                            e.consistency_ratio, e.first_seen, e.last_seen, e.verified_by_owner
                            """);

    @Override
    public List<WorkEdge> edgesWithin(TrackId track) {
        return jdbc.query(EDGES_WITHIN_TRACK, (row, index) -> edge(row), track.value());
    }

    private static final String LOOP_COLUMNS =
            """
            id, job_id, member_node_ids, cycle_count, exit_condition, total_work_ms, total_external_wait_ms
            """;

    private static final String SAVE_LOOP =
            """
            insert into loop (id, job_id, member_node_ids, cycle_count, exit_condition,
                              total_work_ms, total_external_wait_ms)
            values (?, ?, ?, ?, ?, ?, ?)
            on conflict (id) do update set
                member_node_ids = excluded.member_node_ids,
                cycle_count = excluded.cycle_count,
                exit_condition = coalesce(loop.exit_condition, excluded.exit_condition),
                total_work_ms = excluded.total_work_ms,
                total_external_wait_ms = excluded.total_external_wait_ms
            """;

    @Override
    public void save(Loop loop) {
        UUID[] members = loop.memberNodeIds().stream().map(WorkNodeId::value).toArray(UUID[]::new);

        jdbc.update(connection -> {
            var statement = connection.prepareStatement(SAVE_LOOP);
            statement.setObject(1, loop.id());
            statement.setObject(2, loop.jobId().value());
            statement.setArray(3, connection.createArrayOf("uuid", members));
            statement.setInt(4, loop.cycleCount());
            statement.setString(5, loop.exit().map(Enum::name).orElse(null));
            statement.setLong(6, loop.totalWorkMs());
            statement.setLong(7, loop.totalExternalWaitMs());
            return statement;
        });
    }

    private static final String LOOPS_OF_JOB =
            "select %s from loop where job_id = ? order by cycle_count desc, id".formatted(LOOP_COLUMNS);

    @Override
    public List<Loop> loopsOf(JobId job) {
        return jdbc.query(LOOPS_OF_JOB, (row, index) -> loop(row), job.value());
    }

    private WorkEdge edge(ResultSet row) throws SQLException {
        return WorkEdge.rehydrated(
                row.getObject("id", UUID.class),
                WorkNodeId.of(row.getObject("from_node", UUID.class)),
                WorkNodeId.of(row.getObject("to_node", UUID.class)),
                EdgeKind.valueOf(row.getString("kind")),
                row.getBigDecimal("weight"),
                row.getInt("observation_count"),
                row.getBigDecimal("consistency_ratio"),
                instant(row.getTimestamp("first_seen")),
                instant(row.getTimestamp("last_seen")),
                row.getBoolean("verified_by_owner"));
    }

    private Loop loop(ResultSet row) throws SQLException {
        String exit = row.getString("exit_condition");
        return Loop.rehydrated(
                row.getObject("id", UUID.class),
                JobId.of(row.getObject("job_id", UUID.class)),
                members(row.getArray("member_node_ids")),
                row.getInt("cycle_count"),
                exit == null ? null : LoopExit.valueOf(exit),
                row.getLong("total_work_ms"),
                row.getLong("total_external_wait_ms"));
    }

    private static List<WorkNodeId> members(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return Arrays.stream((UUID[]) array.getArray()).map(WorkNodeId::of).toList();
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
