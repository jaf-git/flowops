package com.flowops.discovery.infrastructure.persistence;

import com.flowops.discovery.application.shared.port.JoinIntentPort;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.JobId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JoinIntentAdapter implements JoinIntentPort {
    private final JdbcTemplate jdbc;

    public JoinIntentAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void declared(JobId job, BracketId joiner, BracketId joined, UUID by, Instant at) {
        jdbc.update(
                """
                insert into bracket_join_intent (
                    id, job_id, joiner_bracket_id, joined_bracket_id, declared_by, declared_at)
                values (?, ?, ?, ?, ?, ?)
                on conflict do nothing
                """,
                UUID.randomUUID(),
                job.value(),
                joiner.value(),
                joined.value(),
                by,
                Timestamp.from(at));
    }
}
