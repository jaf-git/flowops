package com.flowops.automation.infrastructure.persistence;

import com.flowops.automation.application.shared.port.AppendEventPort;
import com.flowops.automation.domain.AutomationAction;
import com.flowops.shared.notice.SubjectRef;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AutomationEventAdapter implements AppendEventPort {
    private final JdbcTemplate jdbc;

    public AutomationEventAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void recordOnce(AutomationAction action, SubjectRef subject, Instant episodeStartedAt, Instant at) {
        jdbc.update(
                """
                insert into automation_event (id, action, subject_kind, subject_id, rung, occurred_at)
                select ?, ?, ?, ?, null, ?
                 where not exists (
                       select 1 from automation_event
                        where action = ? and subject_id = ? and occurred_at >= ?)
                """,
                UUID.randomUUID(),
                action.name(),
                subject.kind().name(),
                subject.id(),
                Timestamp.from(at),
                action.name(),
                subject.id(),
                Timestamp.from(episodeStartedAt));
    }

    @Override
    public void record(AutomationAction action, SubjectRef subject, Integer rung, Instant at) {
        jdbc.update(
                """
                insert into automation_event (id, action, subject_kind, subject_id, rung, occurred_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                action.name(),
                subject.kind().name(),
                subject.id(),
                rung,
                Timestamp.from(at));
    }
}
