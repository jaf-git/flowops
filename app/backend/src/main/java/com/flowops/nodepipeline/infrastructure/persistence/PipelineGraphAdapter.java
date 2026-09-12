package com.flowops.nodepipeline.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowops.nodepipeline.application.port.PipelineGraphPort;
import com.flowops.nodepipeline.domain.CandidateTemplate;
import com.flowops.nodepipeline.domain.PipelineNode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PipelineGraphAdapter implements PipelineGraphPort {
    private final JdbcTemplate jdbc;

    private final ObjectMapper json;

    public PipelineGraphAdapter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<PipelineNode> nodesMarkedBetween(Instant from, Instant to, int limit) {
        return jdbc.query(
                """
                select n.id, n.job_id, n.text, n.detail, n.creator_id, n.marker_id, n.kind,
                       creator_role.name   as creator_role,
                       performer_role.name as performer_role,
                       n.created_at, n.state, n.direction, n.post_close, n.output_type,
                       n.work_type, n.task_template_id, n.conversation_id,
                       n.title, n.checklist,
                       activity.slug as activity_slug,
                       activity.name as activity_name,
                       coalesce(b.disrupted, false) as disrupted,
                       n.fingerprint_preceding_role_id, n.fingerprint_preceding_direction,
                       n.fingerprint_following_role_id, n.fingerprint_position_in_track,
                       preceding_role.name as preceding_role,
                       following_role.name as following_role
                  from work_node n
                  left join functional_role creator_role   on creator_role.id   = n.creator_role_id
                  left join functional_role performer_role on performer_role.id = n.performer_role_id
                  left join functional_role preceding_role on preceding_role.id = n.fingerprint_preceding_role_id
                  left join functional_role following_role on following_role.id = n.fingerprint_following_role_id
                  left join work_bracket b on b.id = n.bracket_id
                  left join activity on activity.id = n.activity_id
                 where n.created_at >= ? and n.created_at < ?
                 order by n.created_at desc, n.id desc
                 limit ?
                """,
                (ResultSet row, int index) -> node(row),
                Timestamp.from(from),
                Timestamp.from(to),
                limit);
    }

    @Override
    public java.util.Map<String, Integer> nodeCountsByJobBetween(Instant from, Instant to) {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        jdbc.query(
                """
                select n.job_id, count(*) as nodes
                  from work_node n
                 where n.created_at >= ? and n.created_at < ?
                 group by n.job_id
                """,
                (ResultSet row) -> {
                    counts.put(row.getObject("job_id", UUID.class).toString(), row.getInt("nodes"));
                },
                Timestamp.from(from),
                Timestamp.from(to));
        return counts;
    }

    @Override
    public Adoption activityAdoption() {
        return jdbc.queryForObject(
                """
                select count(*) as marks, count(n.activity_id) as naming
                  from work_node n
                 where n.kind not in ('JOB_START', 'JOB_END')
                   and n.direction <> 'COMPLETION'
                """,
                (ResultSet row, int index) -> new Adoption(row.getLong("marks"), row.getLong("naming")));
    }

    @Override
    public java.util.Set<String> directConversations() {
        return new java.util.LinkedHashSet<>(
                jdbc.queryForList("select id from conversation where kind = 'DIRECT'", String.class));
    }

    @Override
    public java.util.Optional<String> commonestWorkTypeOf(java.util.UUID performer) {
        if (performer == null) {
            return java.util.Optional.empty();
        }
        return jdbc
                .query(
                        """
                        select work_type
                          from work_node
                         where performer_id = ?
                           and work_type is not null
                           and kind not in ('JOB_START', 'JOB_END')
                         group by work_type
                         order by count(*) desc
                         limit 1
                        """,
                        (row, index) -> row.getString("work_type"),
                        performer)
                .stream()
                .findFirst();
    }

    @Override
    public java.util.Map<String, String> templateTitles() {
        java.util.Map<String, String> titles = new java.util.LinkedHashMap<>();
        jdbc.query("select id, title from task_template", row -> {
            titles.put(row.getObject("id", UUID.class).toString(), row.getString("title"));
        });
        return titles;
    }

    @Override
    public List<CandidateTemplate> discoveredTemplates() {
        return templatesWhere("t.discovered_by_pipeline = true", "t.title");
    }

    @Override
    public List<CandidateTemplate> library() {
        return templatesWhere("t.status = 'APPROVED'", "t.id");
    }

    private List<CandidateTemplate> templatesWhere(String condition, String ordering) {
        return jdbc.query(
                """
                select t.id, t.title, t.description, t.work_type, t.responsible_role, t.output_kind,
                       t.expected_output, t.required_input, t.completion_criteria, t.checklist,
                       t.status, t.approved_at, t.keywords
                  from task_template t
                 where %s
                 order by %s
                """
                        .formatted(condition, ordering),
                (ResultSet row, int index) -> new CandidateTemplate(
                        row.getObject("id", UUID.class).toString(),
                        row.getString("title"),
                        row.getString("description"),
                        row.getString("work_type"),
                        row.getString("responsible_role"),
                        row.getString("output_kind"),
                        row.getString("expected_output"),
                        row.getString("required_input"),
                        row.getString("completion_criteria"),
                        textArray(row.getString("checklist")),
                        row.getString("status"),
                        row.getTimestamp("approved_at") == null
                                ? null
                                : row.getTimestamp("approved_at")
                                        .toInstant()
                                        .atZone(ZoneOffset.UTC)
                                        .toLocalDate(),
                        sqlArray(row, "keywords"),
                        null,
                        null,
                        null));
    }

    @Override
    public List<com.flowops.nodepipeline.domain.job.PipelineJob> jobsFor(java.util.Collection<String> jobIds) {
        if (jobIds.isEmpty()) {
            return List.of();
        }
        UUID[] ids = jobIds.stream().map(UUID::fromString).toArray(UUID[]::new);
        return jdbc.query(
                """
                select j.id, j.name, j.status, j.standing, j.shape_eligible, j.is_rework,
                       j.rework_of_job_id, j.last_activity_at, c.kind as counterparty_kind
                  from job j
                  left join counterparty c on c.id = j.counterparty_id
                 where j.id = any(?)
                 order by j.id
                """,
                (ResultSet row, int index) -> new com.flowops.nodepipeline.domain.job.PipelineJob(
                        row.getObject("id", UUID.class).toString(),
                        row.getString("name"),
                        row.getString("status"),
                        row.getBoolean("standing"),
                        row.getBoolean("shape_eligible"),
                        row.getBoolean("is_rework"),
                        row.getObject("rework_of_job_id", UUID.class) == null
                                ? null
                                : row.getObject("rework_of_job_id", UUID.class).toString(),
                        row.getString("counterparty_kind"),
                        row.getTimestamp("last_activity_at")
                                .toInstant()
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()),
                (Object) ids);
    }

    @Override
    public List<com.flowops.nodepipeline.domain.job.ProcessShape> processShapes() {
        java.util.Map<UUID, java.util.List<String>> steps = new java.util.LinkedHashMap<>();
        jdbc.query(
                """
                select s.template_id, s.task_template_id
                  from step_definition s
                 order by s.template_id, s.position
                """,
                row -> {
                    steps.computeIfAbsent(row.getObject("template_id", UUID.class), key -> new java.util.ArrayList<>())
                            .add(row.getObject("task_template_id", UUID.class).toString());
                });

        return jdbc.query(
                "select id, name from process_template where active order by id",
                (ResultSet row, int index) -> new com.flowops.nodepipeline.domain.job.ProcessShape(
                        row.getObject("id", UUID.class).toString(),
                        row.getString("name"),
                        steps.getOrDefault(row.getObject("id", UUID.class), List.of())));
    }

    private PipelineNode node(ResultSet row) throws SQLException {
        return new PipelineNode(
                row.getObject("id", UUID.class).toString(),
                row.getObject("job_id", UUID.class).toString(),
                row.getString("text"),
                row.getString("detail"),
                row.getObject("creator_id", UUID.class),
                row.getObject("marker_id", UUID.class),
                row.getString("kind"),
                row.getString("creator_role"),
                row.getString("performer_role"),
                row.getTimestamp("created_at")
                        .toInstant()
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate(),
                closureOf(row.getString("state")),
                row.getString("direction"),
                row.getBoolean("post_close"),
                row.getString("output_type"),
                row.getString("work_type"),
                row.getObject("task_template_id", UUID.class) == null
                        ? null
                        : row.getObject("task_template_id", UUID.class).toString(),
                row.getBoolean("disrupted"),
                row.getObject("conversation_id", UUID.class) == null
                        ? null
                        : row.getObject("conversation_id", UUID.class).toString(),
                row.getString("preceding_role"),
                row.getString("fingerprint_preceding_direction"),
                row.getString("following_role"),
                row.getObject("fingerprint_position_in_track", Integer.class),
                row.getString("title"),
                readChecklist(row.getString("checklist")),
                row.getString("activity_slug"),
                row.getString("activity_name"),
                row.getTimestamp("created_at").toInstant());
    }

    private List<String> readChecklist(String stored) throws SQLException {
        if (stored == null) {
            return null;
        }
        try {
            return json.readValue(stored, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException failure) {
            throw new SQLException("stored checklist is not a list of strings", failure);
        }
    }

    private static PipelineNode.Closure closureOf(String state) {
        if (state == null) {
            return PipelineNode.Closure.UNKNOWN;
        }
        return switch (state) {
            case "LAPSED" -> PipelineNode.Closure.LAPSED;
            case "ANSWERED" -> PipelineNode.Closure.UNKNOWN;
            default -> PipelineNode.Closure.MARKED;
        };
    }

    private static List<String> sqlArray(ResultSet row, String column) throws SQLException {
        java.sql.Array array = row.getArray(column);
        if (array == null) {
            return List.of();
        }
        return Arrays.stream((Object[]) array.getArray())
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .toList();
    }

    private static List<String> textArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception unreadable) {
            return List.of();
        }
    }
}
