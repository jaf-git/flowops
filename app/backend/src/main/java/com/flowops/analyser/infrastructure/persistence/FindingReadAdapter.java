package com.flowops.analyser.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowops.analyser.application.shared.port.FindingReadPort;
import com.flowops.analyser.domain.Category;
import com.flowops.analyser.domain.Confidence;
import com.flowops.analyser.domain.Finding;
import com.flowops.analyser.domain.FindingContext;
import com.flowops.analyser.domain.Lifecycle;
import com.flowops.analyser.domain.Severity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FindingReadAdapter implements FindingReadPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public FindingReadAdapter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private static final String LATEST_RUN =
            """
            select id, window_from, window_to, started_at
              from analysis_run
             where reached_stage = 'ANALYSE'

               and produced_by = 'ANALYSER'
             order by started_at desc
             limit 1
            """;

    private static final String FINDINGS_OF_RUN =
            """
            select id, detector, stage, subject_kind, subject_key, subject_name, headline, because, context,
                   category, severity, confidence, reach, reach_of, action, lifecycle, times_seen,
                   first_seen_at, created_at
              from analysis_finding
             where run_id = ?
            """;

    private static final String ONE_FINDING =
            """
            select id, detector, stage, subject_kind, subject_key, subject_name, headline, because, context,
                   category, severity, confidence, reach, reach_of, action, lifecycle, times_seen,
                   first_seen_at, created_at
              from analysis_finding
             where id = ?
            """;

    private static final String EVIDENCE_OF_RUN =
            """
            select fs.finding_id, fs.subject_kind, fs.subject_id
              from finding_subject fs
              join analysis_finding af on af.id = fs.finding_id
             where af.run_id = ?
             order by fs.subject_kind, fs.subject_id
            """;

    private static final String EVIDENCE_OF_FINDING =
            """
            select finding_id, subject_kind, subject_id
              from finding_subject
             where finding_id = ?
             order by subject_kind, subject_id
            """;

    @Override
    public Optional<Run> latestFindings() {
        List<Object[]> runs = jdbc.query(LATEST_RUN, (row, index) -> new Object[] {
            row.getObject("id", UUID.class),
            instant(row, "window_from"),
            instant(row, "window_to"),
            instant(row, "started_at")
        });
        if (runs.isEmpty()) {
            return Optional.empty();
        }

        Object[] run = runs.getFirst();
        UUID runId = (UUID) run[0];
        Map<UUID, Map<Finding.EvidenceKind, List<String>>> evidence = evidence(EVIDENCE_OF_RUN, runId);

        List<Row> findings = jdbc.query(FINDINGS_OF_RUN, (row, index) -> rowOf(row, evidence), runId);

        return Optional.of(new Run(runId, (Instant) run[1], (Instant) run[2], (Instant) run[3], findings));
    }

    @Override
    public Optional<Row> byId(UUID id) {
        Map<UUID, Map<Finding.EvidenceKind, List<String>>> evidence = evidence(EVIDENCE_OF_FINDING, id);
        return jdbc.query(ONE_FINDING, (row, index) -> rowOf(row, evidence), id).stream()
                .findFirst();
    }

    private Map<UUID, Map<Finding.EvidenceKind, List<String>>> evidence(String query, UUID key) {
        Map<UUID, Map<Finding.EvidenceKind, List<String>>> byFinding = new LinkedHashMap<>();
        jdbc.query(
                query,
                row -> {
                    UUID findingId = row.getObject("finding_id", UUID.class);
                    Finding.EvidenceKind kind = evidenceKind(row.getString("subject_kind"));
                    if (kind != null) {
                        byFinding
                                .computeIfAbsent(findingId, id -> new LinkedHashMap<>())
                                .computeIfAbsent(kind, k -> new ArrayList<>())
                                .add(row.getString("subject_id"));
                    }
                },
                key);
        return byFinding;
    }

    private static Finding.EvidenceKind evidenceKind(String stored) {
        try {
            return Finding.EvidenceKind.valueOf(stored);
        } catch (IllegalArgumentException notOurs) {
            return null;
        }
    }

    private Row rowOf(ResultSet row, Map<UUID, Map<Finding.EvidenceKind, List<String>>> evidence) throws SQLException {
        UUID id = row.getObject("id", UUID.class);
        String detector = row.getString("detector");
        String subject = row.getString("subject_key");

        Timestamp firstSeen = row.getTimestamp("first_seen_at");
        Timestamp created = row.getTimestamp("created_at");

        return new Row(
                id,
                detector + ':' + subject,
                analyserOf(detector),
                kindOf(detector),
                row.getString("stage"),
                row.getString("subject_kind"),
                subject,
                row.getString("subject_name"),
                readContext(row.getString("context")),
                category(row.getString("category"), id),
                row.getString("headline"),
                readBecause(row.getString("because")),
                evidence.getOrDefault(id, Map.of()),
                named(row.getString("severity"), Severity::valueOf),
                named(row.getString("confidence"), Confidence::valueOf),
                row.getInt("reach"),
                row.getObject("reach_of", Integer.class),
                row.getString("action"),
                named(row.getString("lifecycle"), Lifecycle::valueOf),
                row.getInt("times_seen"),
                (firstSeen == null ? created : firstSeen).toInstant());
    }

    private static Category category(String stored, UUID findingId) {
        if (stored == null) {
            throw new IllegalStateException("finding " + findingId
                    + " was written by the ANALYSE stage with no category, which no analyser can do");
        }
        return Category.valueOf(stored);
    }

    private static <T> T named(String stored, java.util.function.Function<String, T> of) {
        return stored == null ? null : of.apply(stored);
    }

    private static String analyserOf(String detector) {
        int split = detector.indexOf(':');
        return split < 0 ? detector : detector.substring(0, split);
    }

    private static String kindOf(String detector) {
        int split = detector.indexOf(':');
        return split < 0 ? "" : detector.substring(split + 1);
    }

    private List<String> readBecause(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(stored, new TypeReference<List<String>>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException unreadable) {
            throw new IllegalStateException("a finding's reasons could not be read: " + stored, unreadable);
        }
    }

    private FindingContext readContext(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> written = json.readValue(stored, new TypeReference<Map<String, Object>>() {});
            return new FindingContext(
                    strings(written.get("clients")),
                    strings(written.get("projects")),
                    written.get("engagements") instanceof Number count ? count.intValue() : 0,
                    strings(written.get("workTypes")),
                    moment(written.get("from")),
                    moment(written.get("to")));
        } catch (com.fasterxml.jackson.core.JsonProcessingException unreadable) {
            throw new IllegalStateException("a finding's context could not be read: " + stored, unreadable);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object value) {
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }

    private static Instant moment(Object value) {
        return value instanceof String text ? Instant.parse(text) : null;
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
