package com.flowops.analyser.infrastructure.persistence;

import com.flowops.analyser.application.shared.port.DismissalPort;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DismissalAdapter implements DismissalPort {
    private static final String SUBJECT_TYPE = "ANALYSIS_FINDING";

    private static final String DISMISSED = "DISMISSED";

    private final JdbcTemplate jdbc;

    public DismissalAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String RECORD =
            """
            insert into insight_decision
                (id, kind, subject_type, subject_id, finding_key, decision, subject_fingerprint,
                 decider_user_id, decided_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    @Override
    public void dismiss(String findingKey, String analyser, String fingerprint, UUID deciderUserId, Instant decidedAt) {
        jdbc.update(
                RECORD,
                UUID.randomUUID(),
                analyser,
                SUBJECT_TYPE,
                subjectIdOf(findingKey),
                findingKey,
                DISMISSED,
                fingerprint,
                deciderUserId,
                Timestamp.from(decidedAt));
    }

    private static final String CURRENT =
            """
            select distinct on (finding_key)
                   finding_key, decision, subject_fingerprint, decided_at
              from insight_decision
             where subject_type = ?
             order by finding_key, decided_at desc
            """;

    @Override
    public Map<String, InForce> current() {
        Map<String, InForce> inForce = new HashMap<>();
        jdbc.query(
                CURRENT,
                row -> {
                    if (DISMISSED.equals(row.getString("decision"))) {
                        String key = row.getString("finding_key");
                        inForce.put(
                                key,
                                new InForce(
                                        key,
                                        row.getString("subject_fingerprint"),
                                        row.getTimestamp("decided_at").toInstant()));
                    }
                },
                SUBJECT_TYPE);
        return inForce;
    }

    private static UUID subjectIdOf(String findingKey) {
        return UUID.nameUUIDFromBytes(findingKey.getBytes(StandardCharsets.UTF_8));
    }
}
