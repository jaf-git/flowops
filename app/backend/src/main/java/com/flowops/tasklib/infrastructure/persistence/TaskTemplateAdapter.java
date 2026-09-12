package com.flowops.tasklib.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowops.tasklib.application.port.TaskTemplatePort;
import com.flowops.tasklib.domain.OutputKind;
import com.flowops.tasklib.domain.TaskTemplate;
import com.flowops.tasklib.domain.TemplateDetails;
import com.flowops.tasklib.domain.TemplateMetadata;
import com.flowops.tasklib.domain.TemplateStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class TaskTemplateAdapter implements TaskTemplatePort {
    private static final String COLUMNS =
            "id, title, description, type, priority, estimated_hours, checklist, status, author_id,"
                    + " times_used, rejection_reason, converted_to_process_template_id, created_at, updated_at,"
                    + " responsible_role, trigger_note, required_input, expected_output, output_kind,"
                    + " completion_criteria,"
                    + " approved_at, discovered_by_pipeline";

    private static final double RESEMBLANCE_FLOOR = 0.3;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public TaskTemplateAdapter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<TaskTemplate> byId(UUID id) {
        return jdbc.query("select " + COLUMNS + " from task_template where id = ?", mapper(), id).stream()
                .findFirst();
    }

    @Override
    public List<TaskTemplate> byIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query("select " + COLUMNS + " from task_template where id = any(?)", mapper(), (Object)
                ids.toArray(UUID[]::new));
    }

    @Override
    public Optional<TaskTemplate> approvedNamed(String title) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        return jdbc
                .query(
                        "select " + COLUMNS + " from task_template"
                                + " where status = 'APPROVED' and lower(btrim(title)) = lower(btrim(?))",
                        mapper(),
                        title)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<TaskTemplate> earliestDraftNamed(String title) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        return jdbc
                .query(
                        "select " + COLUMNS + " from task_template"
                                + " where status = 'DRAFT' and lower(btrim(title)) = lower(btrim(?))"
                                + " order by created_at, id limit 1",
                        mapper(),
                        title)
                .stream()
                .findFirst();
    }

    @Override
    public void recordDiscovered(UUID templateId, DiscoveredFacts facts) {
        jdbc.update(
                """
                update task_template
                   set work_type = case
                           when exists (select 1 from work_type_vocabulary v where v.code = ?)
                               then ?
                           else work_type
                       end,
                       keywords   = ?,
                       discovered_by_pipeline = true,
                       description         = case when status = 'DRAFT' then ? else description end,
                       checklist           = case when status = 'DRAFT' then ?::jsonb else checklist end,
                       required_input      = case when status = 'DRAFT' then ? else required_input end,
                       completion_criteria = case when status = 'DRAFT' then ? else completion_criteria end,
                       updated_at = now()
                 where id = ?
                """,
                facts.workType(),
                facts.workType(),
                facts.keywords().toArray(String[]::new),
                facts.description(),
                writeChecklist(facts.checklist()),
                facts.requiredInput(),
                facts.completionCriteria(),
                templateId);
    }

    @Override
    public void save(TaskTemplate template) {
        jdbc.update(
                """
                insert into task_template (id, title, description, type, priority, estimated_hours,
                                           checklist, status, author_id, times_used, rejection_reason,
                                           converted_to_process_template_id, created_at, updated_at,
                                           responsible_role, trigger_note, required_input,
                                           expected_output, output_kind, completion_criteria,
                                           approved_at)
                values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        case when ?::text = 'APPROVED' then ?::timestamptz end)
                on conflict (id) do update set
                    title = excluded.title,
                    description = excluded.description,
                    type = excluded.type,
                    priority = excluded.priority,
                    estimated_hours = excluded.estimated_hours,
                    checklist = excluded.checklist,
                    status = excluded.status,
                    rejection_reason = excluded.rejection_reason,
                    converted_to_process_template_id = excluded.converted_to_process_template_id,
                    responsible_role = excluded.responsible_role,
                    trigger_note = excluded.trigger_note,
                    required_input = excluded.required_input,
                    expected_output = excluded.expected_output,
                    output_kind = excluded.output_kind,
                    completion_criteria = excluded.completion_criteria,
                    updated_at = excluded.updated_at,
                    approved_at = case
                        when excluded.status = 'APPROVED' and task_template.approved_at is null
                            then excluded.updated_at
                        else task_template.approved_at
                    end
                """,
                template.id(),
                template.details().title(),
                template.details().description(),
                template.details().type(),
                template.details().priority(),
                template.details().estimatedHours(),
                writeChecklist(template.details().checklist()),
                template.status().name(),
                template.authorId(),
                template.timesUsed(),
                template.rejectionReason(),
                template.convertedToProcessTemplateId(),
                java.sql.Timestamp.from(template.createdAt()),
                java.sql.Timestamp.from(template.updatedAt()),
                template.metadata().responsibleRole(),
                template.metadata().triggerNote(),
                template.metadata().requiredInput(),
                template.metadata().expectedOutput(),
                template.metadata().outputKind() == null
                        ? null
                        : template.metadata().outputKind().name(),
                template.metadata().completionCriteria(),
                template.status().name(),
                java.sql.Timestamp.from(template.updatedAt()));
    }

    @Override
    public List<TaskTemplate> search(TemplateQuery query, UUID viewer) {
        List<Object> arguments = new ArrayList<>();
        String where = whereClause(query, viewer, arguments);
        arguments.add(query.size());
        arguments.add(query.page() * query.size());
        return jdbc.query(
                "select " + COLUMNS + " from task_template " + where + orderBy(query.sort()) + " limit ? offset ?",
                mapper(),
                arguments.toArray());
    }

    @Override
    public int count(TemplateQuery query, UUID viewer) {
        List<Object> arguments = new ArrayList<>();
        String where = whereClause(query, viewer, arguments);
        Integer total =
                jdbc.queryForObject("select count(*) from task_template " + where, Integer.class, arguments.toArray());
        return total == null ? 0 : total;
    }

    @Override
    public List<TaskTemplate> awaitingApproval() {
        return jdbc.query(
                "select " + COLUMNS + " from task_template where status = 'PROPOSED' order by created_at", mapper());
    }

    @Override
    public List<String> typesInUse() {
        return jdbc.queryForList(
                """
                select distinct type from task_template
                where type is not null and status in ('APPROVED', 'PROPOSED')
                order by type
                """,
                String.class);
    }

    @Override
    public List<Resemblance> resembling(String title, int limit) {
        if (title == null || title.isBlank()) {
            return List.of();
        }
        return jdbc.query(
                "select " + COLUMNS + ", similarity(unaccent(lower(title)), unaccent(lower(?))) as closeness"
                        + " from task_template where status = 'APPROVED'"
                        + " and similarity(unaccent(lower(title)), unaccent(lower(?))) > ?"
                        + " order by closeness desc, times_used desc limit ?",
                (row, index) -> new Resemblance(mapper().mapRow(row, index), row.getDouble("closeness")),
                title,
                title,
                RESEMBLANCE_FLOOR,
                limit);
    }

    @Override
    public List<DraftCandidate> draftCandidates() {
        return jdbc.query(
                """
                select (array_agg(title order by created_at desc))[1] as newest_title,
                       array_agg(distinct title) as variants,
                       count(*) as drafts,
                       min(created_at) as first_seen,
                       max(created_at) as last_seen,
                       array_agg(id order by created_at) as ids
                from (
                    select id, title, created_at,
                           unaccent(lower(btrim(regexp_replace(title, '\\s+', ' ', 'g')))) as job
                    from task_template
                    where status = 'DRAFT'
                ) drafts
                group by job
                order by count(*) desc, max(created_at) desc
                """,
                (row, index) -> new DraftCandidate(
                        row.getString("newest_title"),
                        List.of((String[]) row.getArray("variants").getArray()),
                        row.getInt("drafts"),
                        row.getTimestamp("first_seen").toInstant(),
                        row.getTimestamp("last_seen").toInstant(),
                        List.of((UUID[]) row.getArray("ids").getArray())));
    }

    @Override
    public void recordUse(UUID id) {
        jdbc.update("update task_template set times_used = times_used + 1 where id = ?", id);
    }

    private String whereClause(TemplateQuery query, UUID viewer, List<Object> arguments) {
        List<String> clauses = new ArrayList<>();

        clauses.add("(status <> 'DRAFT' or author_id = ?)");
        arguments.add(viewer);

        if (query.status() != null) {
            clauses.add("status = ?");
            arguments.add(query.status().name());
        } else {
            clauses.add("status in ('APPROVED', 'DRAFT')");
        }
        if (query.mine()) {
            clauses.add("author_id = ?");
            arguments.add(viewer);
        }
        if (query.type() != null && !query.type().isBlank()) {
            clauses.add("type = ?");
            arguments.add(query.type());
        }
        if (query.text() != null && !query.text().isBlank()) {
            clauses.add("(lower(title) like ? or lower(coalesce(description, '')) like ?)");
            String pattern = "%" + query.text().toLowerCase(Locale.ROOT) + "%";
            arguments.add(pattern);
            arguments.add(pattern);
        }
        return "where " + String.join(" and ", clauses) + " ";
    }

    private String orderBy(TemplateSort sort) {
        return switch (sort == null ? TemplateSort.MOST_USED : sort) {
            case MOST_USED -> "order by times_used desc, created_at desc ";
            case NEWEST -> "order by created_at desc ";
            case ALPHABETICAL -> "order by lower(title) ";
        };
    }

    private String writeChecklist(List<String> checklist) {
        try {
            return json.writeValueAsString(checklist == null ? List.of() : checklist);
        } catch (Exception failure) {
            throw new IllegalStateException("a checklist of plain strings could not be written", failure);
        }
    }

    private RowMapper<TaskTemplate> mapper() {
        return (ResultSet row, int index) -> new TaskTemplate(
                row.getObject("id", UUID.class),
                new TemplateDetails(
                        row.getString("title"),
                        row.getString("description"),
                        row.getString("type"),
                        row.getString("priority"),
                        row.getBigDecimal("estimated_hours") == null
                                ? null
                                : row.getBigDecimal("estimated_hours").stripTrailingZeros(),
                        readChecklist(row.getString("checklist"))),
                TemplateStatus.valueOf(row.getString("status")),
                row.getObject("author_id", UUID.class),
                row.getInt("times_used"),
                row.getString("rejection_reason"),
                row.getObject("converted_to_process_template_id", UUID.class),
                new TemplateMetadata(
                        row.getString("responsible_role"),
                        row.getString("trigger_note"),
                        row.getString("required_input"),
                        row.getString("expected_output"),
                        row.getString("output_kind") == null ? null : OutputKind.valueOf(row.getString("output_kind")),
                        row.getString("completion_criteria")),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant(),
                row.getTimestamp("approved_at") == null
                        ? null
                        : row.getTimestamp("approved_at").toInstant(),
                row.getBoolean("discovered_by_pipeline"));
    }

    private List<String> readChecklist(String stored) throws SQLException {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(stored, new TypeReference<List<String>>() {});
        } catch (Exception failure) {
            throw new SQLException("stored checklist is not a list of strings", failure);
        }
    }
}
