package com.flowops.aiinsight.infrastructure.persistence;

import com.flowops.aiinsight.application.port.OrderingObservationPort;
import com.flowops.aiinsight.domain.OrderingPairDerivation.TaskObservation;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderingObservationAdapter implements OrderingObservationPort {
    private static final String ORDERABLE_SQL =
            """
            select t.id, t.process_instance_id, t.title, x.closed_at
            from task t
            join process_instance i on i.id = t.process_instance_id
            join lateral (
                select max(s.occurred_at) as closed_at
                from task_state_transition s
                where s.task_id = t.id and s.to_state = 'CLOSED'
            ) x on true
            where x.closed_at is not null
            order by t.process_instance_id, x.closed_at
            """;

    private static final String RECURRING_SQL =
            """
            select t.id, t.process_instance_id, t.title, x.closed_at
            from task t
            join lateral (
                select max(s.occurred_at) as closed_at
                from task_state_transition s
                where s.task_id = t.id and s.to_state = 'CLOSED'
            ) x on true
            where x.closed_at is not null
            order by x.closed_at
            """;

    private final JdbcTemplate jdbc;

    public OrderingObservationAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<TaskObservation> orderableWork() {
        return jdbc.query(ORDERABLE_SQL, OrderingObservationAdapter::observation);
    }

    @Override
    public List<TaskObservation> recurringWork() {
        return jdbc.query(RECURRING_SQL, OrderingObservationAdapter::observation);
    }

    private static TaskObservation observation(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        return new TaskObservation(
                row.getObject("id", UUID.class),
                row.getObject("process_instance_id", UUID.class),
                normalised(row.getString("title")),
                instant(row.getTimestamp("closed_at")));
    }

    private static String normalised(String title) {
        if (title == null) {
            return null;
        }
        String folded = title.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        return folded.isEmpty() ? null : folded;
    }

    private static Instant instant(Timestamp stamp) {
        return stamp == null ? null : stamp.toInstant();
    }
}
