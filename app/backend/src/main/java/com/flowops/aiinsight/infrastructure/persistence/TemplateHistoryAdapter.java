package com.flowops.aiinsight.infrastructure.persistence;

import com.flowops.aiinsight.application.port.TemplateHistoryPort;
import com.flowops.aiinsight.domain.BlockPatternDetection.BlockOccurrence;
import com.flowops.aiinsight.domain.FalseDependencyDetection.EdgeObservation;
import com.flowops.aiinsight.domain.MissingStepDetection.AttachedStep;
import com.flowops.aiinsight.domain.SlowStepDetection.StepDuration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TemplateHistoryAdapter implements TemplateHistoryPort {
    private final JdbcTemplate jdbc;

    public TemplateHistoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public History of(UUID templateId) {
        String name = jdbc
                .query(
                        "select name from process_template where id = ?",
                        (row, index) -> row.getString("name"),
                        templateId)
                .stream()
                .findFirst()
                .orElse(null);

        Integer completed = jdbc.queryForObject(
                "select count(*) from process_instance where template_id = ? and state = 'COMPLETE'",
                Integer.class,
                templateId);

        Integer neverCompleted = jdbc.queryForObject(
                "select count(*) from process_instance where template_id = ? and state <> 'COMPLETE'",
                Integer.class,
                templateId);

        List<AttachedStep> attached = jdbc.query(
                """
                select s.instance_id, s.title, s.position
                from instance_step s
                join process_instance i on i.id = s.instance_id
                where i.template_id = ? and i.state = 'COMPLETE' and s.origin = 'ATTACHED'
                order by s.instance_id, s.position
                """,
                (row, index) -> new AttachedStep(
                        row.getObject("instance_id", UUID.class), row.getString("title"), row.getInt("position")),
                templateId);

        List<StepDuration> durations = jdbc.query(
                """
                select s.title,
                       coalesce(sum(case when p.phase_kind = 'ACTIVE'
                                         then extract(epoch from (p.ended_at - p.started_at)) * 1000 end), 0)::bigint as work_ms,
                       coalesce(sum(case when p.phase_kind = 'BLOCKED'
                                         then extract(epoch from (p.ended_at - p.started_at)) * 1000 end), 0)::bigint as blocked_ms,
                       coalesce(sum(case when p.phase_kind = 'WAIT'
                                         then extract(epoch from (p.ended_at - p.started_at)) * 1000 end), 0)::bigint as waiting_ms,
                       coalesce(sum(case when p.phase_kind in ('REVIEW', 'APPROVAL')
                                         then extract(epoch from (p.ended_at - p.started_at)) * 1000 end), 0)::bigint as review_ms
                from instance_step s
                join process_instance i on i.id = s.instance_id
                left join task_phase_timer p on p.task_id = s.task_id and p.ended_at is not null
                where i.template_id = ? and i.state = 'COMPLETE' and s.task_id is not null
                group by s.id, s.title
                """,
                (row, index) -> new StepDuration(
                        row.getString("title"),
                        row.getLong("work_ms"),
                        row.getLong("blocked_ms"),
                        row.getLong("waiting_ms"),
                        row.getLong("review_ms")),
                templateId);

        List<BlockOccurrence> blocks = jdbc.query(
                """
                select s.instance_id, s.title, x.reason
                from task_state_transition x
                join instance_step s on s.task_id = x.task_id
                join process_instance i on i.id = s.instance_id
                where i.template_id = ? and i.state = 'COMPLETE'
                  and x.to_state = 'BLOCKED' and x.reason is not null
                order by x.occurred_at
                """,
                (row, index) -> new BlockOccurrence(
                        row.getObject("instance_id", UUID.class), row.getString("title"), row.getString("reason")),
                templateId);

        List<EdgeObservation> edges = jdbc.query(
                """
                select d.instance_id,
                       dependent.title as dependent_title,
                       upstream.title as depends_on_title,
                       min(p.started_at) < upstream.closed_at as started_early
                from instance_step_dependency d
                join instance_step dependent on dependent.id = d.dependent_step_id
                join instance_step upstream on upstream.id = d.depends_on_step_id
                join process_instance i on i.id = d.instance_id
                join task_phase_timer p on p.task_id = dependent.task_id and p.phase_kind = 'ACTIVE'
                where i.template_id = ? and i.state = 'COMPLETE'
                  and upstream.closed_at is not null
                  and upstream.skipped_at is null
                  and dependent.task_id is not null
                group by d.instance_id, dependent.title, upstream.title, upstream.closed_at
                having min(p.started_at) is not null
                """,
                (row, index) -> new EdgeObservation(
                        row.getObject("instance_id", UUID.class),
                        row.getString("dependent_title"),
                        row.getString("depends_on_title"),
                        row.getBoolean("started_early")),
                templateId);

        Map<Integer, String> planned = new LinkedHashMap<>();
        jdbc.query(
                        """
                        select distinct on (s.position) s.position, s.title
                        from instance_step s
                        join process_instance i on i.id = s.instance_id
                        where i.template_id = ? and s.origin = 'DEFINITION'
                        order by s.position, s.title
                        """,
                        (row, index) -> Map.entry(row.getInt("position"), row.getString("title")),
                        templateId)
                .forEach(entry -> planned.putIfAbsent(entry.getKey(), entry.getValue()));

        Instant first = instant(jdbc.queryForObject(
                "select min(started_at) from process_instance where template_id = ? and state = 'COMPLETE'",
                Timestamp.class,
                templateId));
        Instant last = instant(jdbc.queryForObject(
                """
                select max(s.closed_at) from instance_step s
                join process_instance i on i.id = s.instance_id
                where i.template_id = ? and i.state = 'COMPLETE'
                """,
                Timestamp.class,
                templateId));

        return new History(
                name,
                completed == null ? 0 : completed,
                neverCompleted == null ? 0 : neverCompleted,
                null,
                attached,
                durations,
                blocks,
                edges,
                planned,
                shapeOf(templateId),
                first,
                last);
    }

    private List<String> shapeOf(UUID templateId) {
        List<String> steps = jdbc.query(
                "select task_template_id from step_definition where template_id = ?"
                        + " order by position, task_template_id",
                (row, index) -> "step:" + row.getString("task_template_id"),
                templateId);

        List<String> edges = jdbc.query(
                """
                select dependent.task_template_id as dependent_work, upstream.task_template_id as depends_on_work
                from step_dependency d
                join step_definition dependent on dependent.id = d.dependent_step_id
                join step_definition upstream on upstream.id = d.depends_on_step_id
                where d.template_id = ?
                order by dependent.task_template_id, upstream.task_template_id
                """,
                (row, index) -> "edge:" + row.getString("depends_on_work") + ">" + row.getString("dependent_work"),
                templateId);

        List<String> shape = new ArrayList<>(steps);
        shape.addAll(edges);
        return List.copyOf(shape);
    }

    private static Instant instant(Timestamp stamp) {
        return stamp == null ? null : stamp.toInstant();
    }
}
