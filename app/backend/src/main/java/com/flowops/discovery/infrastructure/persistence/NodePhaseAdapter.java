package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.NodePhasePort;
import com.flowops.discovery.domain.enums.PhaseKind;
import com.flowops.discovery.domain.enums.WaitingOn;
import com.flowops.discovery.domain.model.NodePhase;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class NodePhaseAdapter implements NodePhasePort {
    private final JdbcTemplate jdbc;

    public NodePhaseAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String PHASE_COLUMNS = "id, work_node_id, phase, started_at, ended_at, waiting_on";

    private static final String OPEN_PHASE =
            """
            insert into node_phase_row (id, work_node_id, phase, started_at, ended_at, waiting_on)
            values (?, ?, ?, ?, ?, ?)
            on conflict (id) do update set
                ended_at = excluded.ended_at
            """;

    @Override
    public void open(NodePhase phase) {
        write(phase);
    }

    @Override
    public void seal(NodePhase phase) {
        write(phase);
    }

    private void write(NodePhase phase) {
        jdbc.update(
                OPEN_PHASE,
                phase.id(),
                phase.workNodeId().value(),
                phase.kind().name(),
                Timestamp.from(phase.startedAt()),
                phase.endedAt().map(Timestamp::from).orElse(null),
                phase.waitingOn().map(Enum::name).orElse(null));
    }

    private static final String FIND_OPEN_PHASE =
            """
            select %s
            from node_phase_row
            where work_node_id = ?
              and ended_at is null
            order by started_at desc
            limit 1
            """
                    .formatted(PHASE_COLUMNS);

    @Override
    public Optional<NodePhase> openPhaseOf(WorkNodeId node) {
        return jdbc.query(FIND_OPEN_PHASE, (row, index) -> phase(row), node.value()).stream()
                .findFirst();
    }

    private static final String PHASES_OF_NODE =
            "select %s from node_phase_row where work_node_id = ? order by started_at, id".formatted(PHASE_COLUMNS);

    @Override
    public List<NodePhase> phasesOf(WorkNodeId node) {
        return jdbc.query(PHASES_OF_NODE, (row, index) -> phase(row), node.value());
    }

    private static final String MEDIAN_WORK_PHASE =
            """
            select percentile_cont(0.5) within group (
                       order by extract(epoch from (p.ended_at - p.started_at))
                   ) as median_seconds
            from node_phase_row p
            join work_node n on n.id = p.work_node_id
            where n.track_id = ?
              and p.phase = 'WORK'
              and p.ended_at is not null
            """;

    @Override
    public Optional<Duration> medianWorkPhaseOf(TrackId track) {
        Double seconds = jdbc.queryForObject(MEDIAN_WORK_PHASE, Double.class, track.value());
        return seconds == null ? Optional.empty() : Optional.of(Duration.ofMillis(Math.round(seconds * 1000)));
    }

    private NodePhase phase(ResultSet row) throws SQLException {
        String waitingOn = row.getString("waiting_on");
        return NodePhase.rehydrated(
                row.getObject("id", UUID.class),
                WorkNodeId.of(row.getObject("work_node_id", UUID.class)),
                PhaseKind.valueOf(row.getString("phase")),
                instant(row.getTimestamp("started_at")),
                instant(row.getTimestamp("ended_at")),
                waitingOn == null ? null : WaitingOn.valueOf(waitingOn));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
