package com.flowops.analyser.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowops.analyser.application.shared.port.FindingStorePort;
import com.flowops.analyser.domain.Finding;
import com.flowops.analyser.domain.FindingContext;
import com.flowops.analyser.domain.Lifecycle;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FindingStoreAdapter implements FindingStorePort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public FindingStoreAdapter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private static final String PREVIOUS =
            """
            select distinct on (detector, subject_kind, subject_key)
                   detector, subject_key, reach, reach_of, times_seen, first_seen_at, lifecycle, created_at
              from analysis_finding
             where detector || ':' || subject_key = any (?)
             order by detector, subject_kind, subject_key, created_at desc
            """;

    @Override
    public Map<String, PreviousSighting> previousSightingsOf(Iterable<String> keys) {
        List<String> wanted = new java.util.ArrayList<>();
        keys.forEach(wanted::add);
        if (wanted.isEmpty()) {
            return Map.of();
        }

        Map<String, PreviousSighting> found = new HashMap<>();
        jdbc.query(
                PREVIOUS,
                row -> {
                    String key = row.getString("detector") + ':' + row.getString("subject_key");
                    Timestamp firstSeen = row.getTimestamp("first_seen_at");
                    Timestamp created = row.getTimestamp("created_at");
                    found.put(
                            key,
                            new PreviousSighting(
                                    row.getInt("reach"),
                                    row.getObject("reach_of", Integer.class),
                                    (firstSeen == null ? created : firstSeen).toInstant(),
                                    row.getInt("times_seen"),
                                    Optional.ofNullable(row.getString("lifecycle"))
                                            .map(Lifecycle::valueOf)));
                },
                new Object[] {wanted.toArray(String[]::new)});
        return found;
    }

    private static final String STORE =
            """
            insert into analysis_finding
                (id, run_id, detector, stage, subject_kind, subject_key, subject_name, headline,
                 sample_size, category, severity, confidence, reach, reach_of, action, because,
                 context, lifecycle, first_seen_at, times_seen, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb),
                    cast(? as jsonb), ?, ?, ?, ?)
            """;

    private static final String STORE_EVIDENCE =
            """
            insert into finding_subject (finding_id, subject_kind, subject_id)
            values (?, ?, ?::uuid)
            on conflict do nothing
            """;

    @Override
    public void store(
            UUID runId,
            Finding finding,
            Presentation presentation,
            Lifecycle lifecycle,
            Instant firstSeenAt,
            int timesSeen,
            Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                STORE,
                id,
                runId,
                detectorOf(finding),
                presentation.stage().name(),
                finding.subjectKind().name(),
                finding.subject(),
                presentation.subjectName(),
                finding.headline(),
                finding.evidenceCount(),
                finding.category() == null ? null : finding.category().name(),
                finding.severity() == null ? null : finding.severity().name(),
                finding.confidence() == null ? null : finding.confidence().name(),
                finding.reach(),
                finding.reachOf(),
                finding.action(),
                writeBecause(finding.because()),
                writeContext(presentation.context()),
                lifecycle.name(),
                Timestamp.from(firstSeenAt),
                timesSeen,
                Timestamp.from(now));

        finding.evidence()
                .forEach((kind, ids) -> ids.forEach(subjectId -> {
                    if (isUuid(subjectId)) {
                        jdbc.update(STORE_EVIDENCE, id, kind.name(), subjectId);
                    }
                }));
    }

    private static String detectorOf(Finding finding) {
        return finding.analyser() + ':' + finding.kind();
    }

    private String writeBecause(List<String> because) {
        if (because.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(because);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("a finding's reasons could not be written: " + because, failure);
        }
    }

    private String writeContext(FindingContext context) {
        if (context == null || context.isEmpty()) {
            return null;
        }
        Map<String, Object> written = new LinkedHashMap<>();
        if (!context.clients().isEmpty()) {
            written.put("clients", context.clients());
        }
        if (!context.projects().isEmpty()) {
            written.put("projects", context.projects());
        }
        if (context.engagements() > 0) {
            written.put("engagements", context.engagements());
        }
        if (!context.workTypes().isEmpty()) {
            written.put("workTypes", context.workTypes());
        }
        if (context.from() != null) {
            written.put("from", context.from().toString());
        }
        if (context.to() != null) {
            written.put("to", context.to().toString());
        }
        try {
            return json.writeValueAsString(written);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("a finding's context could not be written: " + written, failure);
        }
    }

    private static boolean isUuid(String candidate) {
        try {
            UUID.fromString(candidate);
            return true;
        } catch (IllegalArgumentException notOne) {
            return false;
        }
    }
}
