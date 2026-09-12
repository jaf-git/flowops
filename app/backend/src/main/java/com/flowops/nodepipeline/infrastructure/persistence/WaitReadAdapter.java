package com.flowops.nodepipeline.infrastructure.persistence;

import com.flowops.nodepipeline.application.port.WaitReadPort;
import com.flowops.nodepipeline.domain.wait.BracketClose;
import com.flowops.nodepipeline.domain.wait.WaitKind;
import com.flowops.nodepipeline.domain.wait.WaitSpan;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WaitReadAdapter implements WaitReadPort {
    private final JdbcTemplate jdbc;

    public WaitReadAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<JobWindow> windowOf(UUID jobId) {
        return jdbc
                .query(
                        """
                        select j.id, j.opened_at, j.closed_at
                          from job j
                         where j.id = ?
                        """,
                        (ResultSet row, int index) -> new JobWindow(
                                row.getObject("id", UUID.class),
                                instant(row.getTimestamp("opened_at")),
                                instant(row.getTimestamp("closed_at"))),
                        jobId)
                .stream()
                .findFirst();
    }

    @Override
    public List<WaitSpan> waitsIn(UUID jobId) {
        return jdbc.query(
                """
                select w.kind, w.opened_at, w.satisfied_at, w.cancelled_at,
                       awaited.close_kind as awaited_close_kind,
                       awaited.closed_at  as awaited_closed_at
                  from work_node_wait w
                  join work_bracket waiting on waiting.id = w.bracket_id
                  left join work_bracket awaited on awaited.id = w.on_bracket_id
                 where waiting.job_id = ?
                 order by w.opened_at, w.id
                """,
                (ResultSet row, int index) -> span(row),
                jobId);
    }

    private static WaitSpan span(ResultSet row) throws SQLException {
        return new WaitSpan(
                WaitKind.valueOf(row.getString("kind")),
                instant(row.getTimestamp("opened_at")),
                instant(row.getTimestamp("satisfied_at")),
                instant(row.getTimestamp("cancelled_at")),
                BracketClose.read(row.getString("awaited_close_kind")),
                instant(row.getTimestamp("awaited_closed_at")));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
