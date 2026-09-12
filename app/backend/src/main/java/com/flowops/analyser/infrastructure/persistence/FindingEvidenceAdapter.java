package com.flowops.analyser.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowops.analyser.application.shared.port.FindingEvidencePort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FindingEvidenceAdapter implements FindingEvidencePort {
    private final JdbcTemplate jdbc;

    private final ObjectMapper json;

    public FindingEvidenceAdapter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Evidence evidenceFor(UUID findingId) {
        List<Node> nodes = jdbc.query(NODES, this::node, findingId);
        List<Template> templates = jdbc.query(TEMPLATES, FindingEvidenceAdapter::template, findingId);
        List<Job> jobs = jdbc.query(JOBS, FindingEvidenceAdapter::job, findingId);
        List<Bracket> brackets = jdbc.query(BRACKETS, FindingEvidenceAdapter::bracket, findingId);
        List<Wait> waits = jdbc.query(WAITS, FindingEvidenceAdapter::waitRow, findingId);

        return new Evidence(nodes, templates, jobs, brackets, waits, spreadOf(nodes));
    }

    private static final String NODES =
            """
            select distinct on (n.id)
                   n.id, n.job_id, j.name as job_name, n.conversation_id,
                   e.message_id, m.body as message_text,
                   n.text, n.title, n.detail, n.checklist, n.work_type, n.node_role,
                   mk.id as marker_id, mk.display_name as marker_name,
                   mr.name as marker_role, md.name as marker_department,
                   pf.id as performer_id, pf.display_name as performer_name,
                   pr.name as performer_role, pd.name as performer_department
              from finding_subject s
              join work_node n            on n.id = s.subject_id
              left join job j             on j.id = n.job_id
              left join work_node_evidence e on e.work_node_id = n.id
              left join message m         on m.id = e.message_id
              left join auth_user mk      on mk.id = n.marker_id
              left join auth_user pf      on pf.id = n.performer_id
              left join functional_role mr on mr.id = n.creator_role_id
              left join functional_role pr on pr.id = n.performer_role_id
              left join department md      on md.id = mr.department_id
              left join department pd      on pd.id = pr.department_id
             where s.finding_id = ? and s.subject_kind = 'NODE'
             order by n.id, e.added_at
            """;

    private static final String TEMPLATES =
            """
            select t.id, t.title, t.status, t.work_type, t.responsible_role, t.checklist
              from finding_subject s
              join task_template t on t.id = s.subject_id
             where s.finding_id = ? and s.subject_kind = 'TEMPLATE'
             order by t.title
            """;

    private static final String JOBS =
            """
            select j.id, j.name, j.status
              from finding_subject s
              join job j on j.id = s.subject_id
             where s.finding_id = ? and s.subject_kind = 'JOB'
             order by j.name
            """;

    private static final String BRACKETS =
            """
            select b.id, b.job_id, j.name as job_name, b.work_type, b.project_label,
                   b.state, b.close_kind, b.output_value, b.opened_at, b.closed_at,
                   case when b.closed_at is null then null
                        else round(extract(epoch from (b.closed_at - b.opened_at)) / 60)::bigint
                   end as minutes
              from finding_subject s
              join work_bracket b on b.id = s.subject_id
              left join job j     on j.id = b.job_id
             where s.finding_id = ? and s.subject_kind = 'BRACKET'
             order by b.opened_at
            """;

    private static final String WAITS =
            """
            select w.id, w.bracket_id, b.job_id, j.name as job_name, w.kind, w.reason,
                   w.opened_at, w.satisfied_at, w.cancelled_at,
                   case when w.satisfied_at is null then null
                        else round(extract(epoch from (w.satisfied_at - w.opened_at)) / 86400)::bigint
                   end as days
              from finding_subject s
              join work_node_wait w   on w.id = s.subject_id
              left join work_bracket b on b.id = w.bracket_id
              left join job j          on j.id = b.job_id
             where s.finding_id = ? and s.subject_kind = 'WAIT'
             order by w.opened_at
            """;

    private Node node(ResultSet row, int index) throws SQLException {
        return new Node(
                row.getObject("id", UUID.class),
                row.getObject("job_id", UUID.class),
                row.getString("job_name"),
                row.getObject("conversation_id", UUID.class),
                row.getObject("message_id", UUID.class),
                row.getString("message_text"),
                row.getString("text"),
                row.getString("title"),
                row.getString("detail"),
                checklist(row.getString("checklist")),
                row.getString("work_type"),
                row.getString("node_role"),
                new Person(
                        row.getObject("marker_id", UUID.class),
                        row.getString("marker_name"),
                        row.getString("marker_role"),
                        row.getString("marker_department")),
                new Person(
                        row.getObject("performer_id", UUID.class),
                        row.getString("performer_name"),
                        row.getString("performer_role"),
                        row.getString("performer_department")));
    }

    private static Template template(ResultSet row, int index) throws SQLException {
        return new Template(
                row.getObject("id", UUID.class),
                row.getString("title"),
                row.getString("status"),
                row.getString("work_type"),
                row.getString("responsible_role"),
                textArray(row.getString("checklist")));
    }

    private static Job job(ResultSet row, int index) throws SQLException {
        return new Job(row.getObject("id", UUID.class), row.getString("name"), row.getString("status"));
    }

    private static Bracket bracket(ResultSet row, int index) throws SQLException {
        return new Bracket(
                row.getObject("id", UUID.class),
                row.getObject("job_id", UUID.class),
                row.getString("job_name"),
                row.getString("work_type"),
                row.getString("project_label"),
                row.getString("state"),
                row.getString("close_kind"),
                row.getString("output_value"),
                instant(row, "opened_at"),
                instant(row, "closed_at"),
                row.getObject("minutes", Long.class));
    }

    private static Wait waitRow(ResultSet row, int index) throws SQLException {
        return new Wait(
                row.getObject("id", UUID.class),
                row.getObject("bracket_id", UUID.class),
                row.getObject("job_id", UUID.class),
                row.getString("job_name"),
                row.getString("kind"),
                row.getString("reason"),
                instant(row, "opened_at"),
                instant(row, "satisfied_at"),
                instant(row, "cancelled_at"),
                row.getObject("days", Long.class));
    }

    private static java.time.Instant instant(ResultSet row, String column) throws SQLException {
        java.sql.Timestamp stamp = row.getTimestamp(column);
        return stamp == null ? null : stamp.toInstant();
    }

    private static Spread spreadOf(List<Node> nodes) {
        Set<String> departments = new LinkedHashSet<>();
        Set<String> roles = new LinkedHashSet<>();

        for (Node node : nodes) {
            add(departments, node.marker().department());
            add(departments, node.performer().department());
            add(roles, node.marker().role());
            add(roles, node.performer().role());
        }

        return new Spread(List.copyOf(departments), List.copyOf(roles), departments.size() > 1);
    }

    private static void add(Set<String> into, String value) {
        if (value != null && !value.isBlank()) {
            into.add(value);
        }
    }

    private List<String> checklist(String stored) throws SQLException {
        if (stored == null) {
            return null;
        }
        try {
            return json.readValue(stored, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException failure) {
            throw new SQLException("stored checklist is not a list of strings", failure);
        }
    }

    private static List<String> textArray(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }

        List<String> out = new ArrayList<>();
        for (String piece : stored.replace("[", "").replace("]", "").split(",")) {
            String cleaned = piece.trim().replaceAll("^\"|\"$", "");
            if (!cleaned.isEmpty()) {
                out.add(cleaned);
            }
        }
        return List.copyOf(out);
    }
}
