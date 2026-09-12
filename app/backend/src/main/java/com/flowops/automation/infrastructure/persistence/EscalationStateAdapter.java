package com.flowops.automation.infrastructure.persistence;

import com.flowops.automation.application.shared.port.EscalationStatePort;
import com.flowops.automation.domain.EscalationEpisode;
import com.flowops.automation.domain.Rung;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class EscalationStateAdapter implements EscalationStatePort {
    private static final String THE_LIVE_EPISODE =
            """
            select id, task_id, rung, last_fired_at
            from escalation_state
            where task_id = ? and resolved_at is null
            """;

    private static final String BEGIN_AN_EPISODE =
            """
            insert into escalation_state (id, task_id, rung, last_fired_at, resolved_at)
            values (?, ?, 0, ?, null)
            on conflict (task_id) where resolved_at is null do nothing
            """;

    private static final String ADVANCE_IF_NOBODY_ELSE_HAS =
            """
            update escalation_state
            set rung = ?, last_fired_at = ?
            where id = ? and rung = ? and last_fired_at = ? and resolved_at is null
            """;

    private static final String CLOSE_THE_EPISODE =
            """
            update escalation_state
            set resolved_at = ?
            where id = ? and resolved_at is null
            """;

    private final JdbcTemplate jdbc;

    public EscalationStateAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<EscalationEpisode> openEpisode(UUID taskId) {
        return jdbc
                .query(
                        THE_LIVE_EPISODE,
                        (row, index) -> new EscalationEpisode(
                                row.getObject("id", UUID.class),
                                row.getObject("task_id", UUID.class),
                                Rung.atIndex(row.getInt("rung")),
                                row.getTimestamp("last_fired_at").toInstant()),
                        taskId)
                .stream()
                .findFirst();
    }

    @Override
    public EscalationEpisode open(UUID taskId, Instant overdueSince) {
        jdbc.update(BEGIN_AN_EPISODE, UUID.randomUUID(), taskId, Timestamp.from(overdueSince));
        return openEpisode(taskId)
                .orElseThrow(() -> new IllegalStateException(
                        "an episode was opened for task %s and could not be read back".formatted(taskId)));
    }

    @Override
    public boolean advance(EscalationEpisode seen, Rung to, Instant firedAt) {
        return jdbc.update(
                        ADVANCE_IF_NOBODY_ELSE_HAS,
                        to.index(),
                        Timestamp.from(firedAt),
                        seen.id(),
                        seen.rung().index(),
                        Timestamp.from(seen.lastFiredAt()))
                == 1;
    }

    @Override
    public void resolve(UUID episodeId, Instant at) {
        jdbc.update(CLOSE_THE_EPISODE, Timestamp.from(at), episodeId);
    }
}
