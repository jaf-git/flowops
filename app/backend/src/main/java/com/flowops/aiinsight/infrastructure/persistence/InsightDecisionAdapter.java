package com.flowops.aiinsight.infrastructure.persistence;

import com.flowops.aiinsight.application.port.InsightDecisionPort;
import com.flowops.aiinsight.domain.DecisionOutcome;
import com.flowops.aiinsight.domain.FindingKey;
import com.flowops.aiinsight.domain.InsightIdentity;
import com.flowops.aiinsight.domain.InsightKind;
import com.flowops.aiinsight.domain.RecordedDecision;
import com.flowops.aiinsight.domain.SubjectFingerprint;
import com.flowops.aiinsight.domain.SubjectType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class InsightDecisionAdapter implements InsightDecisionPort {
    private final JdbcTemplate jdbc;

    public InsightDecisionAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(
            InsightIdentity identity,
            DecisionOutcome outcome,
            SubjectFingerprint fingerprint,
            UUID deciderUserId,
            Instant decidedAt) {
        jdbc.update(
                """
                insert into insight_decision
                    (id, kind, subject_type, subject_id, finding_key, decision, subject_fingerprint,
                     decider_user_id, decided_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                identity.kind().name(),
                identity.subjectType().name(),
                identity.subjectId(),
                identity.findingKey().value(),
                outcome.name(),
                fingerprint.value(),
                deciderUserId,
                Timestamp.from(decidedAt));
    }

    @Override
    public List<RecordedDecision> decisionsFor(SubjectType subjectType, UUID subjectId) {
        return jdbc.query(
                """
                select kind, subject_type, subject_id, finding_key, decision, subject_fingerprint
                from insight_decision
                where subject_type = ? and subject_id = ?
                order by decided_at desc
                """,
                (row, index) -> new RecordedDecision(
                        new InsightIdentity(
                                InsightKind.valueOf(row.getString("kind")),
                                SubjectType.valueOf(row.getString("subject_type")),
                                row.getObject("subject_id", UUID.class),
                                new FindingKey(row.getString("finding_key"))),
                        DecisionOutcome.valueOf(row.getString("decision")),
                        row.getString("subject_fingerprint")),
                subjectType.name(),
                subjectId);
    }
}
