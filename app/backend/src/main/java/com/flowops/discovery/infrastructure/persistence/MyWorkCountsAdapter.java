package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.MyWorkCountsPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MyWorkCountsAdapter implements MyWorkCountsPort {
    private final JdbcTemplate jdbc;

    public MyWorkCountsAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MyWorkCounts countsFor(UUID viewer, Instant weekStarted) {
        Timestamp since = Timestamp.from(weekStarted);

        MyWorkCounts counts = jdbc.queryForObject(
                """
                select
                    (
                        select count(*)
                        from work_node_wait w
                        join work_bracket blocked_on on blocked_on.id = w.on_bracket_id
                        where blocked_on.performer_ref = ?
                          and w.satisfied_at is null
                          and w.cancelled_at is null
                    ) as waiting_on_you,
                    (
                        select count(*)
                        from work_bracket mine
                        where mine.performer_ref = ?
                          and mine.state in ('OPEN', 'WAITING')
                          and mine.is_boundary = false
                    ) as open_work,
                    (
                        select count(*)
                        from work_bracket finished
                        where finished.performer_ref = ?
                          and finished.close_kind in ('DELIVERED', 'DONE')
                          and finished.closed_at >= ?
                    ) as delivered_this_week
                """,
                (rs, index) -> new MyWorkCounts(
                        rs.getInt("waiting_on_you"), rs.getInt("open_work"), rs.getInt("delivered_this_week")),
                viewer,
                viewer,
                viewer,
                since);

        return counts == null ? new MyWorkCounts(0, 0, 0) : counts;
    }
}
