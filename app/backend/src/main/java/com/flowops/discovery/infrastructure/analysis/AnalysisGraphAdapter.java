package com.flowops.discovery.infrastructure.analysis;

import com.flowops.discovery.application.analysis.AnalysisGraphPort;
import com.flowops.discovery.domain.analysis.GraphWindow;
import com.flowops.discovery.domain.enums.CloseKind;
import com.flowops.discovery.domain.enums.WaitKind;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.JobId;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AnalysisGraphAdapter implements AnalysisGraphPort {
    private final JdbcTemplate jdbc;

    public AnalysisGraphAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public GraphWindow read(Instant from, Instant to) {
        Map<UUID, Map<WaitKind, Duration>> waiting = waitingByBracket(from, to);

        List<GraphWindow.Row> rows = jdbc.query(
                """
                select
                    b.id               as id,
                    b.job_id           as job_id,
                    b.work_type        as work_type,
                    parent.work_type   as parent_work_type,
                    b.close_kind       as close_kind,
                    b.disrupted        as disrupted,
                    b.is_boundary      as is_boundary,
                    b.opened_at        as opened_at,
                    b.closed_at        as closed_at,
                    b.nudged_at is not null as nudged,
                    b.answered_nudge   as answered_nudge,
                    (select count(*) from work_node_wait w
                       where w.bracket_id = b.id and w.satisfied_at is null and w.cancelled_at is null)
                                       as open_waits
                from work_bracket b
                left join work_bracket parent on parent.id = b.parent_bracket_id
                where b.opened_at >= ? and b.opened_at <= ?
                order by b.opened_at
                """,
                (rs, row) -> {
                    UUID id = rs.getObject("id", UUID.class);

                    Instant openedAt = rs.getTimestamp("opened_at").toInstant();
                    Timestamp closed = rs.getTimestamp("closed_at");

                    Instant endedAt = closed == null ? to : closed.toInstant();

                    Map<WaitKind, Duration> waits = waiting.getOrDefault(id, new EnumMap<>(WaitKind.class));

                    Duration elapsed = Duration.between(openedAt, endedAt);
                    Duration waited = waits.values().stream().reduce(Duration.ZERO, Duration::plus);

                    Duration working = elapsed.minus(waited);
                    if (working.isNegative()) {
                        working = Duration.ZERO;
                    }

                    String closeKind = rs.getString("close_kind");

                    return new GraphWindow.Row(
                            BracketId.of(id),
                            JobId.of(rs.getObject("job_id", UUID.class)),
                            rs.getString("work_type"),
                            rs.getString("parent_work_type"),
                            closeKind == null ? null : CloseKind.valueOf(closeKind),
                            rs.getBoolean("disrupted"),
                            rs.getBoolean("is_boundary"),
                            openedAt,
                            closed == null ? null : closed.toInstant(),
                            working,
                            waits,
                            rs.getInt("open_waits"),
                            rs.getBoolean("nudged"),
                            rs.getBoolean("answered_nudge"));
                },
                Timestamp.from(from),
                Timestamp.from(to));

        return new GraphWindow(from, to, rows);
    }

    @Override
    public int waitCount(Instant from, Instant to) {
        Integer count = jdbc.queryForObject(
                "select count(*) from work_node_wait where opened_at >= ? and opened_at <= ?",
                Integer.class,
                Timestamp.from(from),
                Timestamp.from(to));

        return count == null ? 0 : count;
    }

    private Map<UUID, Map<WaitKind, Duration>> waitingByBracket(Instant from, Instant to) {
        Map<UUID, Map<WaitKind, Duration>> byBracket = new HashMap<>();

        jdbc.query(
                """
                select
                    w.bracket_id as bracket_id,
                    w.kind       as kind,
                    sum(extract(epoch from (
                        coalesce(w.satisfied_at, w.cancelled_at, ?) - w.opened_at
                    )))::bigint  as seconds
                from work_node_wait w
                join work_bracket b on b.id = w.bracket_id
                where b.opened_at >= ? and b.opened_at <= ?
                group by w.bracket_id, w.kind
                """,
                rs -> {
                    UUID bracket = rs.getObject("bracket_id", UUID.class);
                    WaitKind kind = WaitKind.valueOf(rs.getString("kind"));
                    long seconds = Math.max(0L, rs.getLong("seconds"));

                    byBracket
                            .computeIfAbsent(bracket, key -> new EnumMap<>(WaitKind.class))
                            .merge(kind, Duration.ofSeconds(seconds), Duration::plus);
                },
                Timestamp.from(to),
                Timestamp.from(from),
                Timestamp.from(to));

        return byBracket;
    }
}
